package com.palmer.billingstatementgenerator.models.statement;

import com.palmer.billingstatementgenerator.client.CatalogClient;
import com.palmer.billingstatementgenerator.client.SettingsClient;
import com.palmer.billingstatementgenerator.client.StatementClient;
import com.palmer.billingstatementgenerator.models.catalog.ServicePackage;
import com.palmer.billingstatementgenerator.models.lineitems.CashAdvanceLineItem;
import com.palmer.billingstatementgenerator.models.lineitems.MerchandiseLineItem;
import com.palmer.billingstatementgenerator.models.lineitems.ServiceLineItem;
import com.palmer.billingstatementgenerator.models.lineitems.SpecialChargeLineItem;
import com.palmer.billingstatementgenerator.util.AppPreferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton context holder for the current billing {@link Statement}.
 * Manages the lifecycle of the active statement, including initialization
 * from the database and reinitialization on full reset.
 *
 * <p>Tracks whether the current statement has unsaved changes via {@link #isDirty()}.
 * The dirty flag is set automatically when any statement field changes, and cleared
 * after a successful save or load.</p>
 *
 * <p>Must be initialized via {@link #init()} before {@link #current()} is called.
 * Calling {@link #init()} again creates a fresh statement, replacing any existing one.</p>
 */
public final class StatementContext {

    private static final Logger log = LoggerFactory.getLogger(StatementContext.class);
    private static final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private static final CatalogClient catalogClient = new CatalogClient();
    private static final SettingsClient settingsClient = new SettingsClient();
    private static Statement current;
    private static Integer savedId;

    private StatementContext() {
    }

    /**
     * Initializes a new {@link Statement}, loading all catalog data from the database
     * and applying the configured sales tax rate from {@link AppPreferences}.
     * Auto-assigns the next available control number.
     * Safe to call multiple times; each call replaces the previous statement.
     */
    public static synchronized void init() {
        log.info("Initializing statement context");
        savedId = null;
        Statement statement = buildFreshStatement();
        statement.setControlNumber(new StatementClient().getNextControlNumber());
        current = statement;
        wireListeners(current);
        dirty.set(false);
        log.debug("Statement context loaded — services={}, merchandise={}, specialCharges={}, cashAdvances={}",
                statement.getServices().size(), statement.getMerchandise().size(),
                statement.getSpecialCharges().size(), statement.getCashAdvances().size());
    }

    /**
     * Loads a previously saved statement by ID into a fresh context. All catalog data
     * is reloaded from the database and the saved values are applied on top.
     * Clears the dirty flag after loading.
     *
     * @param id
     *         the database ID of the statement to load
     */
    public static synchronized void load(int id) {
        log.info("Loading statement id={} into context", id);
        savedId = id;
        Statement statement = buildFreshStatement();
        int packageId = new StatementClient().load(id, statement);
        if (packageId > 0) {
            ServicePackage pkg = catalogClient.findAllServicePackages().stream()
                                              .filter(p -> p.id() == packageId)
                                              .findFirst()
                                              .orElse(null);
            statement.setSelectedPackage(pkg);
        }
        current = statement;
        wireListeners(current);
        dirty.set(false);
    }

    /**
     * Returns the current active {@link Statement}.
     *
     * @return the current statement
     *
     * @throws IllegalStateException
     *         if {@link #init()} has not been called
     */
    public static Statement current() {
        if (current == null) {
            throw new IllegalStateException("StatementContext.init() has not been called");
        }
        return current;
    }

    /**
     * Returns the database ID of the current statement, or {@code null} if the
     * statement has never been saved.
     *
     * @return the saved statement ID, or null
     */
    public static Integer getSavedId() {
        return savedId;
    }

    /**
     * Returns the dirty flag property, which is {@code true} when the current statement
     * has unsaved changes. Suitable for binding to UI elements.
     *
     * @return the dirty {@link BooleanProperty}
     */
    public static BooleanProperty dirtyProperty() {
        return dirty;
    }

    /**
     * Returns {@code true} if the current statement has unsaved changes.
     *
     * @return whether the statement is dirty
     */
    public static boolean isDirty() {
        return dirty.get();
    }

    /**
     * Records the result of a successful save, updating the saved ID and clearing
     * the dirty flag.
     *
     * @param id
     *         the database ID assigned by the save operation
     */
    public static void markSaved(int id) {
        savedId = id;
        dirty.set(false);
    }

    private static Statement buildFreshStatement() {
        Statement statement = new Statement();
        statement.setSalesTaxRate(settingsClient.getSettings().getSalesTaxRate());
        catalogClient.findAllServices()
                     .forEach(s -> statement.getServices().add(new ServiceLineItem(s)));
        catalogClient.findAllMerchandise()
                     .forEach(m -> statement.getMerchandise().add(new MerchandiseLineItem(m)));
        catalogClient.findAllSpecialCharges()
                     .forEach(sc -> statement.getSpecialCharges().add(new SpecialChargeLineItem(sc)));
        catalogClient.findAllCashAdvances()
                     .forEach(ca -> statement.getCashAdvances().add(new CashAdvanceLineItem(ca)));
        return statement;
    }

    private static void wireListeners(Statement statement) {
        statement.controlNumberProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.servicesForNameProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.dateOfDeathProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.placeOfDeathProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.serviceDateProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.reasonForEmbalmingProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.selectedPackageProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.paymentProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.salesTaxRateProperty().addListener((obs, o, n) -> dirty.set(true));
        statement.getServices().forEach(i -> {
            i.selectedProperty().addListener((obs, o, n) -> dirty.set(true));
            i.inPackageProperty().addListener((obs, o, n) -> dirty.set(true));
        });
        statement.getMerchandise().forEach(i -> {
            i.selectedProperty().addListener((obs, o, n) -> dirty.set(true));
            i.priceProperty().addListener((obs, o, n) -> dirty.set(true));
            i.descriptionProperty().addListener((obs, o, n) -> dirty.set(true));
        });
        statement.getSpecialCharges().forEach(i -> {
            i.selectedProperty().addListener((obs, o, n) -> dirty.set(true));
            i.priceProperty().addListener((obs, o, n) -> dirty.set(true));
            i.descriptionProperty().addListener((obs, o, n) -> dirty.set(true));
        });
        statement.getCashAdvances().forEach(i -> {
            i.selectedProperty().addListener((obs, o, n) -> dirty.set(true));
            i.amountProperty().addListener((obs, o, n) -> dirty.set(true));
            i.providerProperty().addListener((obs, o, n) -> dirty.set(true));
        });
    }
}