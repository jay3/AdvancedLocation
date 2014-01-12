package fr.jayps.android;

import android.location.Location;
import android.util.Log;
import android.content.Context;
import android.widget.Toast;

public class AdvancedLocation {
    private static final String TAG = "AdvancedLocation";
    
    protected class LocationWithExtraFields extends Location {

        public float distance = 0; // in m
        public LocationWithExtraFields(Location l) {
            super(l);
            this.distance = _distance;
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


    static final float _minAccuracyIni = 10; // in m
    protected float _minAccuracy = _minAccuracyIni;   // in m
    
    // max value for _minAccuracy
    static final float _maxMinAccuracy = 50;   // in m
    
    // always remember that accuracy is 3x worth on altitude than on latitude/longitude
    static final float _minAccuracyForAltitudeChangeLevel1 = 3; // in m
    static final float _minAltitudeChangeLevel1 = 10; // in m
    static final float _minAccuracyForAltitudeChangeLevel2 = 6; // in m
    static final float _minAltitudeChangeLevel2 = 20; // in m
    static final float _minAccuracyForAltitudeChangeLevel3 = 12; // in m
    static final float _minAltitudeChangeLevel3 = 50; // in m
    static final long _minDeltaTimeForAscentRate = 60 * 1000; // in ms
    static final long _maxDeltaTimeForAscentRate = 3 * 60 * 1000; // in ms

    static final long _minDeltaTimeToSaveLocation = 3000; // in ms
    static final float _minDeltaDistanceToSaveLocation = 20;   // in m
    
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
    protected float _ascentRate = 0; // in m/s
    
    protected float _slope = 0; // in %
    
    // Height of geoid above WGS84 ellipsoid
    protected double _geoidHeight = 0; // in m

    // debug levels
    public int debugLevel = 0;
    public int debugLevelToast = 0;
    public String debugTagPrefix = "";
    
    // constants
    public static final int SKIPPED = 0x0;
    public static final int NORMAL = 0x1;
    public static final int SAVED = 0x2;

    protected Context _context = null;
    public AdvancedLocation() {
        this._context = null;
    }    
    public AdvancedLocation(Context context) {
        this._context = context;
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
    public float getSpeed() {
        if (currentLocation != null) {
            return currentLocation.getSpeed();
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
        return _ascent;
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
        this._geoidHeight = geoidHeight;
    }

    public int onLocationChanged(Location location) {
    	int returnValue = NORMAL;
        long deltaTime = 0;
        float deltaDistance = 0;
        double deltaAscent = 0;
        double deltaAltitude = 0;
        float deltaAccuracy = 0;
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

        if ((lastGoodLocation != null) && ((location.getTime() - lastGoodLocation.getTime()) < 500)) {
            // less than X ms, skip this location
            return SKIPPED;
        }
        
        deltaTime = location.getTime() - lastLocation.getTime();
        deltaDistance = location.distanceTo(lastLocation);
        currentLocation = new LocationWithExtraFields(location);
        
        if (currentLocation.getAccuracy() <= _minAccuracy) {

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
        
            float localAverageSpeed = (float) deltaDistance / ((float) deltaTime / 1000f); // in m/s
            
            //Logger("localAverageSpeed:" + localAverageSpeed + " speed=" + currentLocation.getSpeed());
            
            // additional conditions to compute statistics
            if (
                  isFirstLocation
                ||
                  (localAverageSpeed > _minSpeedToComputeStats)
            ) {
                _elapsedTime += deltaTime;
                _distance += deltaDistance;
                _averageSpeed = (float) _distance / ((float) _elapsedTime / 1000f);

                if (lastGoodAscentLocation == null) {
                    lastGoodAscentLocation = currentLocation;
                    lastGoodAscentLocation2 = currentLocation;
                    lastGoodAscentRateLocation = currentLocation;
                }
                
                deltaAltitude = currentLocation.getAltitude() - lastGoodAscentLocation.getAltitude();
                deltaAccuracy = currentLocation.getAccuracy() - lastGoodAscentLocation.getAccuracy();
                
                if (deltaAltitude < 0 && deltaAccuracy <= -3) {
                	// Goal: during a "climb", if altitude decreases and accuracy is better, update lastGoodAscentLocation
                	// it will avoid use of previously "wrong" (too high) lastGoodAscentLocation with lesser accuracy to compute ascent
                	Logger("altitude decreases and accuracy is better (it decreases of at least 3m), use this position as lastGoodAscentLocation");
                	lastGoodAscentLocation = currentLocation;
                	lastGoodAscentRateLocation = currentLocation;
                	deltaAltitude = 0;
                }
                
                if (Math.abs(deltaAltitude) < 0.5 && deltaAccuracy < 0) {
                    Logger("flat section, and better accuracy, reset lastGoodAscentLocation", 2);
                    lastGoodAscentLocation = currentLocation;
                    deltaAltitude = 0;
                }
                
                if (_testLocationOKForAscent()) {
                	
                    // compute ascent
                    // always remember that accuracy is 3x worth on altitude than on latitude/longitude
                	deltaAscent = Math.floor(deltaAltitude);

                    lastGoodAscentLocation = currentLocation;
                    if (lastGoodAscentLocation2.getAccuracy() > lastGoodAscentLocation.getAccuracy()) {
                        Logger("Update lastGoodAscentLocation2 acc:" + lastGoodAscentLocation2.getAccuracy() +"->"+ lastGoodAscentLocation.getAccuracy(), 2);
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
                        
                        _ascentRate = (float) tmpDeltaAscent / (tmpDeltaTime) * 1000; // m/s
                        
                        if (tmpDeltaDistance != 0) {
                            _slope = (float) tmpDeltaAscent / tmpDeltaDistance; // in %
                        } else {
                            _slope = 0;
                        }
                        
                        Logger("alt:" + lastGoodAscentRateLocation.getAltitude() + "->" + currentLocation.getAltitude() + ":" + tmpDeltaAscent + " _ascentRate:" + _ascentRate + " _slope:" + _slope);
                        
                        lastGoodAscentRateLocation = currentLocation;
                    }
                } // if (_testLocationOKForAscent()) {

                
                nbGoodLocations++;

                if (_testFlatSection(lastGoodAscentRateLocation, currentLocation)) {
                	Logger("slope below 1% on the last 500m, update lastGoodAscentRateLocation");
                	_slope = 0;
                	_ascentRate = 0;
                	lastGoodAscentRateLocation = currentLocation;
                }
                
                long tmpDeltaTime = currentLocation.getTime() - lastGoodAscentRateLocation.getTime();
                if (tmpDeltaTime > _maxDeltaTimeForAscentRate && currentLocation.getAccuracy() < 10) {
                	Logger("lastGoodAscentRateLocation too old ("+tmpDeltaTime+"s) and current accuracy ok ("+currentLocation.getAccuracy()+"m), update lastGoodAscentRateLocation");
                	_slope = 0;
                	_ascentRate = 0;
                	lastGoodAscentRateLocation = currentLocation;
                }

                Logger(currentLocation.getTime()/1000+ " deltaDistance:" + deltaDistance + " deltaTime:" + deltaTime + " deltaAscent:" + deltaAscent + " _ascent:" + _ascent + " _distance: " + _distance + " _averageSpeed: " + _averageSpeed + " _elapsedTime:" + _elapsedTime);
               
                if (_testLocationOKForSave()) {
                    Logger("Location OK to be saved", 2);
                    returnValue = SAVED;
                    lastSavedLocation = currentLocation;
                }
                
            } // additional conditions to compute statistics
            
            lastGoodLocation = currentLocation;
            
        } // if (currentLocation.getAccuracy() <= _minAccuracy) {
        
        lastLocation = currentLocation;
        
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

                s += altitude2CalibrationDelta;
                Logger("altitude2CalibrationDelta:" + s);
                Logger("delta:" + s, LoggerType.TOAST);
            }
        }

        return returnValue;
    }

    public void onAltitudeChanged(double altitude) {
        Logger("onAltitudeChanged: " + altitude + " altitude2CalibrationTime=" + altitude2CalibrationTime + " altitude2CalibrationAccuracy=" + altitude2CalibrationAccuracy + " altitude2CalibrationDelta=" + altitude2CalibrationDelta, 2);
        this.hasAltitude2 = true;
        this.altitude2 = altitude;
    }

    private boolean _testFlatSection(LocationWithExtraFields l1, LocationWithExtraFields l2) {
    	float deltaDistance = l2.distance - l1.distance;
    	double deltaAltitude = l2.getAltitude() - l1.getAltitude();
	    
    	if ((deltaDistance > 500) && (100 * Math.abs(deltaAltitude) < deltaDistance)) {
    		// distance greater than 1000m and slope below 1%: this is a flat portion
    		
    		if (l2.getAccuracy() > 5) {
    			// if l2.getAccuracy() is bad, avoid positive result (wait a bit more for better accuracy?)
    			return false;
    		}
    		// Note: if l1.getAccuracy() was bad, don't avoid positive result (it won't change if we wait)
    		
	    	return true;
	    }
	    return false;
    }
    
    private boolean _testLocationOKForAscent() {
    	if (lastGoodAscentLocation == null) {
    		return false;
    	}
	    float worstAccuracy = Math.max(lastGoodAscentLocation.getAccuracy(), currentLocation.getAccuracy());
	    double deltaAltitude = currentLocation.getAltitude() - lastGoodAscentLocation.getAltitude();
        float deltaAccuracy = currentLocation.getAccuracy() - lastGoodAscentLocation.getAccuracy();
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
	    } else if (Math.abs(deltaAltitude) >= 4 * worstAccuracy) {
	    	Logger("abs(deltaAltitude):" + Math.abs(deltaAltitude) + ">=4*worstAccuracy: 4*" + worstAccuracy);
	    	result = true;
	    } else if (lastGoodAscentLocation2 != null) {
	        float worstAccuracy2 = Math.max(lastGoodAscentLocation2.getAccuracy(), currentLocation.getAccuracy());
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
            if (this.debugLevel >= level) {
                Log.v(this.debugTagPrefix + TAG + ":" + level, s);
            }
        } else {
            if (this.debugLevel >= level) {
                Log.v(this.debugTagPrefix + TAG + ":" + level, s);
            }
        }
    }
}
