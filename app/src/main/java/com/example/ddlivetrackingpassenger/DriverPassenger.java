package com.example.ddlivetrackingpassenger;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class DriverPassenger {
    @SerializedName("driver")
    @Expose
    private String driver;

    @SerializedName("passenger")
    @Expose
    private String passenger;

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getPassenger() {
        return passenger;
    }

    public void setPassenger(String passenger) {
        this.passenger = passenger;
    }
}
