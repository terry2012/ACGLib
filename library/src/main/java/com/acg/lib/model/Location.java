package com.acg.lib.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

/**
 * Model for a location returned by a LocationACG
 * Wraps the Android Location
 * Required by SPARTA for the flow to be correct, otherwise, the flow is inferred by the use of the Android Location
 * object, but this is not the correct flow for ACGs.
 */
public final class Location implements Parcelable {

    private android.location.Location location;

    public Location(@NonNull android.location.Location location) {
        this.location = location;
    }

    protected Location(Parcel in) {
        location = in.readParcelable(android.location.Location.class.getClassLoader());
    }

    public static final Creator<Location> CREATOR = new Creator<Location>() {
        @Override
        public Location createFromParcel(Parcel in) {
            return new Location(in);
        }

        @Override
        public Location[] newArray(int size) {
            return new Location[size];
        }
    };

    public android.location.Location getLocation() {
        return location;
    }

    public double getLatitude() {
        return location.getLatitude();
    }

    public double getLongitude() {
        return location.getLongitude();
    }

    public float getSpeed() {
        return location.getSpeed();
    }

    public long getTime() {
        return location.getTime();
    }

    public float distanceTo(Location location) {
        return this.location.distanceTo(location.getLocation());
    }

    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(location, flags);
    }
}
