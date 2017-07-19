
package fr.jayps.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.Log;
import android.content.Context;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class AdvancedLocation {
    private static final String TAG = "AdvancedLocation";

    protected class LocationWithExtraFields extends Location {
        public float distance = 0; // in m

        // altitude2, a 2nd altitude, provided by a pressure sensor for example
        private double _altitude2 = 0;
        private boolean _hasAltitude2 = false;
        private long _altitude2CalibrationTime = 0;
        private double _altitude2CalibrationDelta = 0;

        public LocationWithExtraFields(Location l) {
            super(l);
            this.distance = _distance;
            this._altitude2 = altitude2;
            this._hasAltitude2 = hasAltitude2;
            this._altitude2CalibrationTime = altitude2CalibrationTime;
            this._altitude2CalibrationDelta = altitude2CalibrationDelta;
        }

        public double getAltitude() {
            if (this._hasAltitude2 && this._altitude2CalibrationTime > 0) {
                return this._altitude2 + this._altitude2CalibrationDelta;
            }
            return super.getAltitude();
        }
        public double getAltitudeFromGps() {
            return super.getAltitude();
        }
        public float getAltitudeAccuracy() {
            if (this._hasAltitude2 && this._altitude2CalibrationTime > 0) {
                // obtained from a pressure sensor, and calibration already done
                return 1; // should be below _minAccuracyForAltitudeChangeLevel1
            }
            return super.getAccuracy();
        }
    }

    protected LocationWithExtraFields currentLocation = null;         // current location
    protected LocationWithExtraFields lastLocation = null;            // last received location
    protected LocationWithExtraFields lastGoodLocation = null;        // last location with accuracy below _minAccuracy
    protected LocationWithExtraFields lastGoodAscentLocation = null;  // last location with changed ascent
    protected LocationWithExtraFields lastGoodAscentLocation2 = null; // other previous location with changed ascent, older and with better accuracy than lastGoodAscentLocation
    protected LocationWithExtraFields lastGoodAscentRateLocation = null;  // last location with changed ascentRate
    protected LocationWithExtraFields lastSavedLocation = null;       // last saved location


    // altitude2, a 2nd altitude, provided by a pressure sensor for example
    protected double altitude2 = 0;
    protected boolean hasAltitude2 = false;
    protected long altitude2CalibrationTime = 0;
    protected float altitude2CalibrationAccuracy = 99;
    protected double altitude2CalibrationDelta = 0;
    // constants used to "calibrate" altitude2
    static final float _minAccuracyForAltitude2Calibration = 5; // in m
    static final float _minDeltaTimeForAltitude2Calibration = 20 * 60 * 1000; // in ms


    static final float _minAccuracyIni = 20; // in m
    protected float _minAccuracy = _minAccuracyIni;   // in m

    // max value for _minAccuracy
    static final float _maxMinAccuracy = 50;   // in m

    // always remember that accuracy is 3x worth on altitude than on latitude/longitude
    static final float _minAccuracyForAltitudeChangeLevel1 = 1; // in m
    static final float _minAltitudeChangeLevel1 = 3; // in m
    static final float _minAccuracyForAltitudeChangeLevel2 = 3; // in m
    static final float _minAltitudeChangeLevel2 = 10; // in m
    static final float _minAccuracyForAltitudeChangeLevel3 = 6; // in m
    static final float _minAltitudeChangeLevel3 = 20; // in m
    static final float _minAccuracyForAltitudeChangeLevel4 = 12; // in m
    static final float _minAltitudeChangeLevel4 = 50; // in m
    static final long _minDeltaTimeForAscentRate = 60 * 1000; // in ms
    static final long _maxDeltaTimeForAscentRate = 3 * 60 * 1000; // in ms

    static final long _minDeltaTimeToSaveLocation = 5 * 60 * 1000; // in ms
    static final float _minDeltaDistanceToSaveLocation = 20;   // in m

    private static final float MAX_ACCURACY_FOR_MAX_SPEED = 12; // in m

    // min speed to compute _elapsedTime or _ascent
    // 0.3m/s <=> 1.08km/h
    static final float _minSpeedToComputeStats = 0.3f; // in m/s

    public int nbOnLocationChanged = 0;
    public int nbGoodLocations = 0;
    protected int _nbBadAccuracyLocations = 0;

    protected float _distance = 0; // in m
    protected double _ascent = 0; // in m
    protected long _elapsedTime = 0; // in ms

    protected float _averageSpeed = 0; // in m/s
    protected float _maxSpeed = 0; // in m/s
    protected float _ascentRate = 0; // in m/s

    protected float _slope = 0; // in %

    protected int _nbAscent = 0;
    private double _nbAscentAltitudeLocalMin = 0;
    private double _nbAscentAltitudeLocalMax = 0;
    private boolean _nbAscentAscentInProgress = false;
    private boolean _nbAscentDescentInProgress = false;
    public static final float MAX_ACCURACY_FOR_NB_ASCENT = 7; // in m
    public static final float NB_ASCENT_DELTA_ALTITUDE = 50; // in m

    // Height of geoid above WGS84 ellipsoid
    protected double _geoidHeight = 0; // in m

    private int _hearRate = 0;
    private int _cadence = 0;
    private float _sensorSpeed = 0;
    private long _sensorSpeedTime = 0;

    // debug levels
    public int debugLevel = 0;
    public int debugLevelToast = 0;
    public String debugTagPrefix = "";

    // constants
    public static final int SKIPPED = 0x0;
    public static final int NORMAL = 0x1;
    public static final int SAVED = 0x2;

    protected Context _context = null;
    private AdvancedLocationDbHelper dbHelper;
    private SQLiteDatabase db;
    private boolean _saveLocation = false;

    public AdvancedLocation() {
        this._context = null;
    }

    public AdvancedLocation(Context context) {
        this._context = context;
        dbHelper = AdvancedLocationDbHelper.getInstance(context);
        db = dbHelper.getWritableDatabase();
    }

    // getters
    public double getAltitude() {
        if (hasAltitude2 && altitude2CalibrationTime > 0) {
            return altitude2 + altitude2CalibrationDelta;
        }

        if (lastGoodLocation != null) {
            return lastGoodLocation.getAltitude();
        }
        return 0;
    }
    public double getAltitudeFromGps() {
        if (currentLocation != null) {
            return currentLocation.getAltitudeFromGps();
        }
        return 0;
    }
    public double getAltitudeFromPressure() {
        return altitude2;
    }


    public double getGoodAltitude() {
        if (lastGoodAscentLocation != null) {
            return lastGoodAscentLocation.getAltitude();
        }
        return 0;
    }

    public float getAccuracy() {
        if (currentLocation != null) {
            return currentLocation.getAccuracy();
        }
        return 0.0f;
    }

    public float getAltitudeAccuracy() {
        if (currentLocation != null) {
            return currentLocation.getAltitudeAccuracy();
        }
        return 0.0f;
    }

    public float getSpeed() {
        if (currentLocation != null) {
            Logger("getSpeed currentLocation time:" + currentLocation.getTime() + " speed:" + currentLocation.getSpeed() + " sensor time:" + _sensorSpeedTime + " speed:" + _sensorSpeed);
            if (_sensorSpeed != 0.0 && _sensorSpeedTime > 0 && currentLocation.getTime() < _sensorSpeedTime + 10 * 1000) {
                // we've got a sensor speed, and no gps speed at least 10s newer
                return _sensorSpeed;
            }
            return currentLocation.getSpeed();
        } else if (_sensorSpeedTime > 0) {
            Logger("getSpeed sensor time:" + _sensorSpeedTime + " speed:" + _sensorSpeed);
            return _sensorSpeed;
        }
        return 0.0f;
    }

    public float getAverageSpeed() {
        if ((_averageSpeed == 0) && (_elapsedTime > 0)) {
            // not yet calculated yet?
            _averageSpeed = (float) _distance / ((float) _elapsedTime / 1000f);
        }
        return _averageSpeed;
    }
    public float getMaxSpeed() {
        return _maxSpeed;
    }
    public int getNbAscent() {
        return _nbAscent;
    }

    public long getElapsedTime() {
        return _elapsedTime;
    }

    public long getTime() {
        if (currentLocation != null) {
            return currentLocation.getTime();
        }
        return 0;
    }

    public float getDistance() {
        return _distance;
    }

    public double getAscent() {
        return Math.floor(_ascent);
    }

    public float getAscentRate() {
        return _ascentRate;
    }

    public float getSlope() {
        return _slope;
    }

    public boolean hasBearing() {
        if (currentLocation != null) {
            return currentLocation.hasBearing();
        }
        return false;
    }

    public float getBearing() {
        if (currentLocation != null) {
            return currentLocation.getBearing();
        }
        return 0;
    }

    public String getBearingText() {
        if (currentLocation != null) {
            // getBearing() is guaranteed to be in the range (0.0, 360.0] if the device has a bearing.
            return bearingText(currentLocation.getBearing());
        }
        return "";
    }

    public static String bearingText(float bearing) {
        String bearingText = "";

        bearing = bearing % 360;
        if (bearing < 0) {
            bearing += 360;
        }

        if (bearing >= 0 && bearing < 22.5) {
            bearingText = "N";
        }

        if (bearing >= 22.5 && bearing < 67.5) {
            bearingText = "NE";
        }

        if (bearing >= 67.5 && bearing < 112.5) {
            bearingText = "E";
        }

        if (bearing >= 112.5 && bearing < 157.5) {
            bearingText = "SE";
        }

        if (bearing >= 157.5 && bearing < 202.5) {
            bearingText = "S";
        }

        if (bearing >= 202.5 && bearing < 247.5) {
            bearingText = "SW";
        }

        if (bearing >= 247.5 && bearing < 292.5) {
            bearingText = "W";
        }

        if (bearing >= 292.5 && bearing < 337.5) {
            bearingText = "NW";
        }

        if (bearing >= 337.5 && bearing < 360) {
            bearingText = "N";
        }

        return bearingText;
    }

    public double getLatitude() {
        if (currentLocation != null) {
            return currentLocation.getLatitude();
        }
        return 0;
    }

    public double getLongitude() {
        if (currentLocation != null) {
            return currentLocation.getLongitude();
        }
        return 0;
    }

    public double getGeoidHeight() {
        return this._geoidHeight;
    }

    public double getAltitudeCalibrationDelta() {
        return this.altitude2CalibrationDelta;
    }

    // setters
    public void setElapsedTime(long elapsedTime) {
        this._elapsedTime = elapsedTime;
    }

    public void setDistance(float distance) {
        this._distance = distance;
    }

    public void setAscent(double ascent) {
        this._ascent = ascent;
    }

    public void setGeoidHeight(double geoidHeight) {
        Logger("setGeoidHeight:" + geoidHeight);

        if (this._geoidHeight != geoidHeight) {
            this._geoidHeight = geoidHeight;

            // force to recalibrate altitude2 (pressure sensor, if we got one)
            hasAltitude2 = false;
            altitude2CalibrationTime = 0;
        }
    }

    public void setAltitudeCalibrationDelta(double altitudeCalibrationDelta) {
        Logger("setAltitudeCalibrationDelta:" + altitudeCalibrationDelta);
        if (altitudeCalibrationDelta != 0 && this.altitude2CalibrationDelta != altitudeCalibrationDelta) {
            this.altitude2CalibrationDelta = altitudeCalibrationDelta;
            this.altitude2CalibrationAccuracy = _minAccuracyForAltitude2Calibration;
            this.altitude2CalibrationTime = 1; // timestamp in the past
        }
    }

    public void setMaxSpeed(float maxSpeed) {
        this._maxSpeed = maxSpeed;
    }
    public void setNbAscent(int nbAscent) {
        // reset internal data
        _nbAscentAltitudeLocalMin = _nbAscentAltitudeLocalMax = 0;
        this._nbAscent = nbAscent;
    }
    public void setSaveLocation(boolean saveLocation) {
        this._saveLocation = saveLocation;
    }

    public int onLocationChanged(Location location, int heartRate, int cadence) {
        int returnValue = NORMAL;
        long deltaTime = 0;
        float deltaDistance = 0;
        double deltaAscent = 0;
        double deltaAltitude = 0;
        float deltaAltitudeAccuracy = 0;
        boolean isFirstLocation = false;

        if (this._geoidHeight != 0) {
        // we get an height of geoid (above WGS84 ellipsoid), use it to correct altitude
            location.setAltitude(location.getAltitude() - this._geoidHeight);
        }

        nbOnLocationChanged++;
        Logger("onLocationChanged: " +nbGoodLocations+"/"+nbOnLocationChanged+" "+(location.getTime()/1000)+","+location.getLatitude()+","+location.getLongitude()+","+location.getAltitude()+"("+this._geoidHeight+"),"+location.getAccuracy());

        if (lastLocation == null) {
            // save 1st location for next call to onLocationChanged()
            lastLocation = new LocationWithExtraFields(location);
            isFirstLocation = true;
        }

        if (location.getAccuracy() > _minAccuracy) {
            _nbBadAccuracyLocations++;
            if (_nbBadAccuracyLocations > 10) {
                float _prevMinAccuracy = _minAccuracy;

                _minAccuracy = (float) Math.floor(1.5f * _minAccuracy);

                if (_minAccuracy > _maxMinAccuracy) {
                    // max value for _minAccuracy
                    _minAccuracy = _maxMinAccuracy;
                }

                if (_minAccuracy != _prevMinAccuracy) {
                    _nbBadAccuracyLocations = 0;

                    Logger("Accuracy to often above _minAccuracy, augment _minAccuracy to " + _minAccuracy,  LoggerType.TOAST);
                }
            }
        }
        if (location.getAccuracy() < MAX_ACCURACY_FOR_MAX_SPEED) {
            _maxSpeed = Math.max(location.getSpeed(), _maxSpeed);
        }
        _hearRate = heartRate;
        _cadence = cadence;

        if ((lastGoodLocation != null) && ((location.getTime() - lastGoodLocation.getTime()) < 500)) {
            // less than X ms, skip this location
            return SKIPPED;
        }

        if (hasAltitude2) {
            if (
                (location.getAccuracy() < altitude2CalibrationAccuracy - 0.5)
                ||
                ((location.getTime() - altitude2CalibrationTime > _minDeltaTimeForAltitude2Calibration)
                && (location.getAccuracy() < _minAccuracyForAltitude2Calibration))
                ) {
                    String s = altitude2CalibrationDelta + "->";
                    altitude2CalibrationTime = location.getTime();
                    altitude2CalibrationAccuracy = location.getAccuracy();
                    altitude2CalibrationDelta = location.getAltitude() - altitude2;

                    // force to restart computations based on altitude
                    lastGoodAscentLocation = null;

                    s += altitude2CalibrationDelta;
                    Logger("altitude2CalibrationDelta:" + s);
                    Logger("delta:" + s, LoggerType.TOAST);
                }
        }

        currentLocation = new LocationWithExtraFields(location);

        if (currentLocation.getAccuracy() <= _minAccuracy) {

            if (lastGoodLocation == null) {
                lastGoodLocation = currentLocation;
            }

            deltaTime = location.getTime() - lastGoodLocation.getTime();
            deltaDistance = location.distanceTo(lastGoodLocation);

            if (currentLocation.getAccuracy() <= (_minAccuracy / 1.5f)) {
                float _prevMinAccuracy = _minAccuracy;

                _minAccuracy = (float) Math.floor(_minAccuracy / 1.5f);

                if (_minAccuracy < _minAccuracyIni) {
                    _minAccuracy = _minAccuracyIni;
                }

                if (_minAccuracy != _prevMinAccuracy) {
                    Logger("Accuracy below _minAccuracy, decrease it to: " + _minAccuracy, LoggerType.TOAST);
                }
            }

            float localAverageSpeed = deltaTime > 0 ? ((float) deltaDistance / ((float) deltaTime / 1000f)) : 0; // in m/s

            //Logger("localAverageSpeed:" + localAverageSpeed + " speed=" + currentLocation.getSpeed());

            // additional conditions to compute statistics
            if (
                isFirstLocation
                ||
                (localAverageSpeed > _minSpeedToComputeStats)
            ) {
                _elapsedTime += deltaTime;
                _distance += deltaDistance;
                _averageSpeed = _elapsedTime > 0 ? ((float) _distance / ((float) _elapsedTime / 1000f)) : 0;

                if (lastGoodAscentLocation == null) {
                    lastGoodAscentLocation = currentLocation;
                    lastGoodAscentLocation2 = currentLocation;
                    lastGoodAscentRateLocation = currentLocation;
                }

                deltaAltitude = currentLocation.getAltitude() - lastGoodAscentLocation.getAltitude();
                deltaAltitudeAccuracy = currentLocation.getAltitudeAccuracy() - lastGoodAscentLocation.getAltitudeAccuracy();

                if (deltaAltitude < 0 && deltaAltitudeAccuracy <= -3) {
                    // Goal: during a "climb", if altitude decreases and accuracy is better, update lastGoodAscentLocation
                    // it will avoid use of previously "wrong" (too high) lastGoodAscentLocation with lesser accuracy to compute ascent
                    Logger("altitude decreases and accuracy is better (it decreases of at least 3m), use this position as lastGoodAscentLocation");
                    lastGoodAscentLocation = currentLocation;
                    lastGoodAscentRateLocation = currentLocation;
                    deltaAltitude = 0;
                }

                if (Math.abs(deltaAltitude) < 0.5 && deltaAltitudeAccuracy < 0) {
                    Logger("flat section, and better accuracy, reset lastGoodAscentLocation", 2);
                    lastGoodAscentLocation = currentLocation;
                    deltaAltitude = 0;
                }

                if (_testLocationOKForAscent()) {
                    // compute ascent
                    // always remember that accuracy is 3x worth on altitude than on latitude/longitude
                    deltaAscent = deltaAltitude;

                    lastGoodAscentLocation = currentLocation;

                    if (lastGoodAscentLocation2.getAltitudeAccuracy() > lastGoodAscentLocation.getAltitudeAccuracy()) {
                        Logger("Update lastGoodAscentLocation2 acc:" + lastGoodAscentLocation2.getAltitudeAccuracy() +"->"+ lastGoodAscentLocation.getAltitudeAccuracy(), 2);
                        lastGoodAscentLocation2 = currentLocation;
                    }

                    if (lastGoodAscentLocation.getTime() - lastGoodAscentLocation2.getTime() > 60 * 10 * 1000) {
                        Logger("lastGoodAscentLocation2 too old", 2);
                        lastGoodAscentLocation2 = currentLocation;
                    }

                    if (deltaAscent > 0) {
                        _ascent += deltaAscent;
                    } else {
                        lastGoodAscentLocation2 = currentLocation;
                        Logger("descent, reset lastGoodAscentLocation2", 2);
                    }

                    // try to compute ascentRate if enough time has elapsed
                    long tmpDeltaTime = currentLocation.getTime() - lastGoodAscentRateLocation.getTime();

                    if (tmpDeltaTime < _minDeltaTimeForAscentRate) {
                        // not enough time since lastGoodAscentRateLocation to compute ascentRate and slope
                        Logger("tmpDeltaTime:" + tmpDeltaTime +"<"+ _minDeltaTimeForAscentRate + " ascentRate skip");
                    } else {
                        double tmpDeltaAscent = Math.floor(currentLocation.getAltitude() - lastGoodAscentRateLocation.getAltitude());
                        float tmpDeltaDistance = _distance - lastGoodAscentRateLocation.distance;

                        _ascentRate = tmpDeltaTime > 0 ? ((float) tmpDeltaAscent / (tmpDeltaTime) * 1000) : 0; // m/s

                        if (tmpDeltaDistance != 0) {
                            _slope = tmpDeltaDistance > 0 ? ((float) tmpDeltaAscent / tmpDeltaDistance) : 0; // in %
                        } else {
                            _slope = 0;
                        }

                        Logger("alt:" + lastGoodAscentRateLocation.getAltitude() + "->" + currentLocation.getAltitude() + ":" + tmpDeltaAscent + " _ascentRate:" + _ascentRate + " _slope:" + _slope);

                        lastGoodAscentRateLocation = currentLocation;
                    }
                } // if (_testLocationOKForAscent()) {

                if (currentLocation.getAccuracy() < MAX_ACCURACY_FOR_NB_ASCENT) {
                    if (_nbAscentAltitudeLocalMin == 0 && _nbAscentAltitudeLocalMax == 0) {
                        // first time only
                        _nbAscentAltitudeLocalMin = _nbAscentAltitudeLocalMax = currentLocation.getAltitude();
                        _nbAscentAscentInProgress = _nbAscentDescentInProgress = false;
                    }
                    _nbAscentAltitudeLocalMin = Math.min(currentLocation.getAltitude(), _nbAscentAltitudeLocalMin);
                    _nbAscentAltitudeLocalMax = Math.max(currentLocation.getAltitude(), _nbAscentAltitudeLocalMax);

                    if (!_nbAscentDescentInProgress && currentLocation.getAltitude() <= _nbAscentAltitudeLocalMax - NB_ASCENT_DELTA_ALTITUDE) {
                        Logger("nbAscent: start new descent", 1);
                        _nbAscentDescentInProgress = true;
                        _nbAscentAscentInProgress = false;
                        _nbAscentAltitudeLocalMin = currentLocation.getAltitude();
                    }
                    if (!_nbAscentAscentInProgress && currentLocation.getAltitude() >= _nbAscentAltitudeLocalMin + NB_ASCENT_DELTA_ALTITUDE) {
                        Logger("nbAscent: start new ascent", 1);
                        _nbAscentAscentInProgress = true;
                        _nbAscentDescentInProgress = false;
                        _nbAscentAltitudeLocalMax = currentLocation.getAltitude();
                        _nbAscent++;
                    }

                    Logger("nbAscent: " + _nbAscentAltitudeLocalMin +"<"+_nbAscentAltitudeLocalMax + " " + currentLocation.getAltitude() + " " + (_nbAscentAscentInProgress ? "ASC" : "NOASC") + " " + (_nbAscentDescentInProgress ? "DSC" : "NODSC"), 2);
                }

                nbGoodLocations++;

                if (_testFlatSection(lastGoodAscentRateLocation, currentLocation)) {
                    Logger("slope below 1% on the last 500m, update lastGoodAscentRateLocation");
                    _slope = 0;
                    _ascentRate = 0;
                    lastGoodAscentRateLocation = currentLocation;
                }

                long tmpDeltaTime = currentLocation.getTime() - lastGoodAscentRateLocation.getTime();
                if (tmpDeltaTime > _maxDeltaTimeForAscentRate && currentLocation.getAltitudeAccuracy() < 10) {
                    Logger("lastGoodAscentRateLocation too old ("+tmpDeltaTime+"s) and current accuracy ok ("+currentLocation.getAltitudeAccuracy()+"m), update lastGoodAscentRateLocation");
                    _slope = 0;
                    _ascentRate = 0;
                    lastGoodAscentRateLocation = currentLocation;
                }

                Logger(currentLocation.getTime()/1000+ " deltaDistance:" + deltaDistance + " deltaTime:" + deltaTime + " deltaAscent:" + deltaAscent + " _ascent:" + _ascent + " _distance: " + _distance + " _averageSpeed: " + _averageSpeed + " _elapsedTime:" + _elapsedTime);

                if (_testLocationOKForSave()) {
                    Logger("Location OK to be saved", 2);
                    returnValue = SAVED;
                    lastSavedLocation = currentLocation;
                    if (_saveLocation) {
                        _saveLocation();
                    }
                }

            } // additional conditions to compute statistics

            lastGoodLocation = currentLocation;

        } // if (currentLocation.getAccuracy() <= _minAccuracy) {

        lastLocation = currentLocation;

        return returnValue;
    }

    // Array of altitude, to compute median of _ALTITUDES2_NB values
    private static int _ALTITUDES2_NB = 5;
    private double[] _altitudes2 = new double[_ALTITUDES2_NB];
    private int _altitudes2_i = 0;

    public void onAltitudeChanged(double altitude) {
        Logger("onAltitudeChanged: " + altitude + " altitude2CalibrationTime=" + altitude2CalibrationTime + " altitude2CalibrationAccuracy=" + altitude2CalibrationAccuracy + " altitude2CalibrationDelta=" + altitude2CalibrationDelta, 2);
        _altitudes2[_altitudes2_i % _ALTITUDES2_NB] = altitude;
        _altitudes2_i++;
        if (_altitudes2_i > _ALTITUDES2_NB) {
            double[] _altitudes2b = Arrays.copyOf(_altitudes2, _ALTITUDES2_NB);;
            Arrays.sort(_altitudes2b);
            this.hasAltitude2 = true;
            this.altitude2 = _altitudes2b[(int) Math.floor(_ALTITUDES2_NB/2)]; // median value
            Logger("altitude=" + altitude + " this.altitude2=" + this.altitude2, 2);
        }
    }

    public void setSensorSpeed(float speed, long time) {
        this._sensorSpeed = speed;
        this._sensorSpeedTime = time;
        Logger("setSensorSpeed:" + _sensorSpeedTime + " speed:" + _sensorSpeed);
    }

    private boolean _testFlatSection(LocationWithExtraFields l1, LocationWithExtraFields l2) {
        float deltaDistance = l2.distance - l1.distance;
        double deltaAltitude = l2.getAltitude() - l1.getAltitude();

        if ((deltaDistance > 500) && (100 * Math.abs(deltaAltitude) < deltaDistance)) {
            // distance greater than 1000m and slope below 1%: this is a flat portion

            if (l2.getAltitudeAccuracy() > 5) {
                // if l2.getAltitudeAccuracy() is bad, avoid positive result (wait a bit more for better accuracy?)
                return false;
            }
            // Note: if l1.getAltitudeAccuracy() was bad, don't avoid positive result (it won't change if we wait)

            return true;
        }
        return false;
    }

    private boolean _testLocationOKForAscent() {
        if (lastGoodAscentLocation == null) {
            return false;
        }

        float worstAccuracy = Math.max(lastGoodAscentLocation.getAltitudeAccuracy(), currentLocation.getAltitudeAccuracy());
        double deltaAltitude = currentLocation.getAltitude() - lastGoodAscentLocation.getAltitude();
        float deltaAccuracy = currentLocation.getAltitudeAccuracy() - lastGoodAscentLocation.getAltitudeAccuracy();
        boolean result = false;

        if ((Math.abs(deltaAltitude) >= _minAltitudeChangeLevel1) && (worstAccuracy <= _minAccuracyForAltitudeChangeLevel1)) {
            Logger("abs(deltaAltitude):" + Math.abs(deltaAltitude) + ">=" + _minAltitudeChangeLevel1 + " & worstAccuracy:" + worstAccuracy + "<=" + _minAccuracyForAltitudeChangeLevel1);
            result = true;
        } else if ((Math.abs(deltaAltitude) >= _minAltitudeChangeLevel2) && (worstAccuracy <= _minAccuracyForAltitudeChangeLevel2)) {
            Logger("abs(deltaAltitude):" + Math.abs(deltaAltitude) + ">=" + _minAltitudeChangeLevel2 + " & worstAccuracy:" + worstAccuracy + "<=" + _minAccuracyForAltitudeChangeLevel2);
            result = true;
        } else if ((Math.abs(deltaAltitude) >= _minAltitudeChangeLevel3) && (worstAccuracy <= _minAccuracyForAltitudeChangeLevel3)) {
            Logger("abs(deltaAltitude):" + Math.abs(deltaAltitude) + ">=" + _minAltitudeChangeLevel3 + " & worstAccuracy:" + worstAccuracy + "<=" + _minAccuracyForAltitudeChangeLevel3);
            result = true;
        } else if ((Math.abs(deltaAltitude) >= _minAltitudeChangeLevel4) && (worstAccuracy <= _minAccuracyForAltitudeChangeLevel4)) {
            Logger("abs(deltaAltitude):" + Math.abs(deltaAltitude) + ">=" + _minAltitudeChangeLevel4 + " & worstAccuracy:" + worstAccuracy + "<=" + _minAccuracyForAltitudeChangeLevel4);
            result = true;
        } else if (Math.abs(deltaAltitude) >= 4 * worstAccuracy) {
            Logger("abs(deltaAltitude):" + Math.abs(deltaAltitude) + ">=4*worstAccuracy: 4*" + worstAccuracy);
            result = true;
        } else if (lastGoodAscentLocation2 != null) {
            float worstAccuracy2 = Math.max(lastGoodAscentLocation2.getAltitudeAccuracy(), currentLocation.getAltitudeAccuracy());
            double deltaAltitude2 = currentLocation.getAltitude() - lastGoodAscentLocation2.getAltitude();
            if (Math.abs(deltaAltitude2) >= 4 * worstAccuracy2) {
                Logger("abs(deltaAltitude2):" + Math.abs(deltaAltitude2) + ">=4*worstAccuracy2: 4*" + worstAccuracy2);
                result = true;
            }
        }

        if (result) {
            Logger("alt:" + lastGoodAscentLocation.getAltitude() + "->" + currentLocation.getAltitude() + ":" + deltaAltitude + " - acc: " + worstAccuracy);
            return true;
        }

        return false;
    }

    private boolean _testLocationOKForSave() {
        if (
        (lastSavedLocation == null) // 1st saved location
        ||
        (currentLocation.getTime() - lastSavedLocation.getTime() >= _minDeltaTimeToSaveLocation)
        ||
        (currentLocation.distanceTo(lastSavedLocation) >= _minDeltaDistanceToSaveLocation)
        ) {
            return true;
        }

        return false;
    }

    private void _saveLocation() {
        ContentValues values = new ContentValues();
        values.put("loca_time", this.getTime());
        values.put("loca_lat", this.getLatitude());
        values.put("loca_lon", this.getLongitude());
        values.put("loca_altitude", this.getAltitude());
        values.put("loca_gps_altitude", this.getAltitudeFromGps());
        values.put("loca_pressure_altitude", this.getAltitudeFromPressure());
        values.put("loca_ascent", this.getAscent());
        values.put("loca_accuracy", this.getAccuracy());
        if (_hearRate > 0) {
            values.put("loca_hr", _hearRate);
        }
        if (_cadence > 0) {
            values.put("loca_cad", _cadence);
        }
        //values.put("loca_comment", "");

        long newRowId = db.insert(
                AdvancedLocationDbHelper.Location.TABLE_NAME,
                null,
                values);
    };

    public String getGPX(boolean extended) {
        StringBuilder gpx = new StringBuilder();
        String creator = "Ventoo";
        if (this._context != null) {
            SensorManager mSensorManager = (SensorManager) _context.getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
                // for Strava https://strava.github.io/api/v3/uploads/
                creator += " with Barometer";
            }
        }
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" creator=\"" + creator + "\" version=\"1.1\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd  http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\" xmlns:pb10=\"http://www.pebblebike.com/GPX/1/0/\">\n");


        String selectQuery = "SELECT _ID, loca_time, loca_lat, loca_lon, loca_altitude, loca_accuracy, loca_comment, loca_ascent, loca_gps_altitude, loca_pressure_altitude, loca_hr, loca_cad FROM " + AdvancedLocationDbHelper.Location.TABLE_NAME + " ORDER BY _ID ASC";
        Cursor cursor = db.rawQuery(selectQuery, null);

        long itemId = -1;
        int trackNumber = 1;
        if (cursor.moveToFirst()) {
            gpx.append("<trk>\n"
                    + "<name>Track #1</name>\n"
                    + "<trkseg>\n");

            long prevTime = -1;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            do {
                /*itemId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(AdvancedLocationDbHelper.Location._ID)
                );*/
                String time = "";


                if (prevTime > 0 && Long.parseLong(cursor.getString(1)) - prevTime > 12 * 3600 * 1000) {
                    trackNumber++;
                    // more than 12 hours since last point? create new track
                    gpx.append("</trkseg>\n</trk>\n<trk>\n<name>Track #" + trackNumber + "</name>\n<trkseg>\n");

                } else if (prevTime > 0 && Long.parseLong(cursor.getString(1)) - prevTime > 2 * 3600 * 1000) {
                    // more than 2 hours since last point? create new segment
                    gpx.append("</trkseg>\n<trkseg>\n");
                }

                Date netDate = (new Date(Long.parseLong(cursor.getString(1))));
                time = sdf.format(netDate);
                time = time.substring(0, time.length() - 2) + ':' + time.substring(time.length() - 2);

                gpx.append("<trkpt lat=\"" + cursor.getString(2) + "\" lon=\"" + cursor.getString(3) + "\">\n"
                        + "  <ele>" + cursor.getString(4) + "</ele>\n"
                        + "  <time>" + time + "</time>\n");
                if (extended || !cursor.isNull(10) || !cursor.isNull(11)) {
                    gpx.append("  <extensions>\n");
                    if (extended) {
                        gpx.append("    <pb10:accuracy>" + cursor.getString(5) + "</pb10:accuracy>\n"
                                 + "    <pb10:ascent>" + cursor.getString(7) + "</pb10:ascent>\n"
                                 + "    <pb10:ele_gps>" + cursor.getString(8) + "</pb10:ele_gps>\n"
                                 + "    <pb10:ele_pressure>" + cursor.getString(9) + "</pb10:ele_pressure>\n");
                    }
                    if (!cursor.isNull(10) || !cursor.isNull(11)) {
                        gpx.append("    <gpxtpx:TrackPointExtension>\n");
                        if (!cursor.isNull(10)) {
                            gpx.append("    <gpxtpx:hr>" + cursor.getString(10) + "</gpxtpx:hr>\n");
                        }
                        if (!cursor.isNull(11)) {
                            gpx.append("    <gpxtpx:cad>" + cursor.getString(11) + "</gpxtpx:cad>\n");
                        }
                        gpx.append("    </gpxtpx:TrackPointExtension>\n");
                    }
                    gpx.append("  </extensions>\n");
                }
                gpx.append("</trkpt>\n");
                prevTime = Long.parseLong(cursor.getString(1));
            } while (cursor.moveToNext());
            gpx.append("</trkseg>\n"
                    + "</trk>\n");

        }
        gpx.append("</gpx>\n");
        //Logger(gpx.toString());
        return gpx.toString();
    }
    public String getRunkeeperJson(String type) {
        StringBuilder json = new StringBuilder();
        StringBuilder hr = new StringBuilder();
        String notes = "Track generated by Ventoo, http://www.pebblebike.com";

        // duration doesn't seem to be taken into account
        json.append("{\"type\": \"" + type + "\", \"notes\": \"" + notes + "\", \"duration\": " + getElapsedTime()/1000 + ",");

        String selectQuery = "SELECT _ID, loca_time, loca_lat, loca_lon, loca_altitude, loca_accuracy, loca_comment, loca_ascent, loca_gps_altitude, loca_pressure_altitude, loca_hr, loca_cad FROM " + AdvancedLocationDbHelper.Location.TABLE_NAME + " ORDER BY _ID ASC";
        //selectQuery += " LIMIT 10";
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            long firstTime = -1;

            String buffer = "";
            do {
                if (buffer != "") {
                    buffer += ", \"type\": \"gps\"}";
                    json.append("," + buffer);
                }
                long deltaTime = Long.parseLong(cursor.getString(1)) - firstTime;
                buffer = "{\"timestamp\": " + (deltaTime/1000) + ",\"altitude\": " + cursor.getString(4) + ",\"longitude\":" + cursor.getString(3) + ",\"latitude\":" + cursor.getString(2);
                if (firstTime < 0) {
                    firstTime = Long.parseLong(cursor.getString(1));
                    String time = "";

                    Date netDate = (new Date(Long.parseLong(cursor.getString(1))));
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.ENGLISH);
                    time = sdf.format(netDate);
                    json.append("\"start_time\": \"" + time + "\", \"path\": [");
                    buffer += ", \"type\": \"start\"}";
                    json.append(buffer);
                    buffer = "";
                }
                if (!cursor.isNull(10)) {
                    if (!hr.toString().isEmpty()) {
                        hr.append(",");
                    }
                    hr.append("{\"timestamp\":" + (deltaTime / 1000) + ", \"heart_rate\":" + cursor.getString(10) + "}");
                }
            } while (cursor.moveToNext());
            if (buffer != "") {
                buffer += ", \"type\": \"end\"}";
                json.append("," + buffer);
            }
            json.append("]");
        }
        if (!hr.toString().isEmpty()) {
            json.append(", \"heart_rate\": [" + hr.toString() + "]");
        }
        json.append("}");
        //Logger(json.toString());
        return json.toString();
    }
    public void resetGPX() {
        String sql = "DELETE FROM " + AdvancedLocationDbHelper.Location.TABLE_NAME;
        db.execSQL(sql);
    }

    // log functions
    private enum LoggerType { LOG, TOAST };

    public void Logger(String s) {
        Logger(s, 1, LoggerType.LOG);
    }

    public void Logger(String s, LoggerType type) {
        Logger(s, 1, type);
    }

    public void Logger(String s, int level) {
        Logger(s, level, LoggerType.LOG);
    }

    public void Logger(String s, int level, LoggerType type) {
        if (type == LoggerType.TOAST) {
            if (this.debugLevelToast >= level) {
                if (this._context != null) {
                    Toast.makeText(this._context, s, Toast.LENGTH_LONG).show();
                }
            }
        }

        if (this.debugLevel >= level) {
            if (level == 2) {
                Log.v(this.debugTagPrefix + TAG + ":" + level, s);
            } else {
                Log.d(this.debugTagPrefix + TAG + ":" + level, s);
            }
        }
    }
}
