package com.nitramite.courier;

/**
 * Created by Martin on 28.2.2016.
 * Handles Parcel data loading and parsing.
 * 17.02.2019 - Changed view to work with pattern strategy method, where couriers are strategies
 */
public class Courier {

    // Logging
    private static final String TAG = "Courier";

    // Strategy interface
    private CourierStrategy courierStrategy;

    // Constructor
    public Courier(/*CourierStrategy initialCourierStrategy_*/) {
        // this.courierStrategy = initialCourierStrategy_;
    }

    // Change courier strategy
    public void setCourierStrategy(CourierStrategy courierStrategy_) {
        this.courierStrategy = courierStrategy_;
    }

    // Execute selected courier strategy
    public ParcelObject executeCourierStrategy(final String parcelCode) {
        return this.courierStrategy.execute(parcelCode);
    }

} // End of class