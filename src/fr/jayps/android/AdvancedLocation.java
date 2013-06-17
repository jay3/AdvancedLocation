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

    protected LocationWithExtraFields lastLocation = null;            // last received location
    protected LocationWithExtraFields lastGoodLocation = null;        // last location with accuracy below _minAccuracy
    protected LocationWithExtraFields lastGoodAscentLocation = null;  // last location with changed ascent
    protected LocationWithExtraFields lastGoodAscentRateLocation = null;  // last location with changed ascentRate
    protected LocationWithExtraFields firstLocation = null;           // first received location
    protected LocationWithExtraFields firstGoodLocation = null;       // first location with accuracy below _minAccuracy
    
    protected float _minAccuracy = 10;   // in m
    protected float _minAccuracyIni = _minAccuracy;
    
    // max value for _minAccuracy
    protected float _maxMinAccuracy = 50;   // in m
    
    // always remember that accuracy is 3x worth on altitude than on latitude/longitude
    protected float _minAccuracyForAltitudeChangeLevel1 = 4; // in m
    protected float _minAltitudeChangeLevel1 = 10; // in m
    protected float _minAccuracyForAltitudeChangeLevel2 = 7; // in m
    protected float _minAltitudeChangeLevel2 = 20; // in m
    protected float _minAccuracyForAltitudeChangeLevel3 = 12; // in m
    protected float _minAltitudeChangeLevel3 = 50; // in m
    protected long _minDeltaTimeForAscentRate = 120*1000; // in ms

    protected long _minDeltaTimeToSaveLocation = 3000; // in ms
    protected float _minDeltaDistanceToSaveLocation = 20;   // in m
    
    // min speed to compute _elapsedTime or _ascent
    // 0.3m/s <=> 1.08km/h
    protected float _minSpeedToComputeStats = 0.3f; // in m/s 
    
    public int nbOnLocationChanged = 0;
    public int nbGoodLocations = 0;
    protected int _nbBadAccuracyLocations = 0;
    
    protected float _distance = 0; // in m
    protected double _ascent = 0; // in m
    protected long _elapsedTime = 0; // in ms

    protected float _averageSpeed = 0; // in m/s
    protected float _ascentRate = 0; // in m/s
    
    protected float _slope = 0; // in %
    
    // debug levels
    public int debugLevel = 0;
    public int debugLevelToast = 0;

    

    protected Context _context = null;
    public AdvancedLocation() {
        this._context = null;
    }    
    public AdvancedLocation(Context context) {
        this._context = context;
    }
    
    // getters
    public double getAltitude() {
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
        if (lastLocation != null) {
            return lastLocation.getAccuracy();
        }
        return 0.0f;
    }    
    public float getSpeed() {
        if (lastLocation != null) {
            return lastLocation.getSpeed();
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

    public void onLocationChanged(Location location) {
        long deltaTime = 0;
        float deltaDistance = 0;
        double deltaAscent = 0;

        nbOnLocationChanged++;
        Logger("onLocationChanged: " +nbGoodLocations+"/"+nbOnLocationChanged+" Alt:"+ location.getAltitude() + "m-" + location.getAccuracy() + "m " + location.getLatitude() + "-" + location.getLongitude());
        
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

        if (firstLocation == null) {
            // 1st location
            firstLocation = lastLocation = new LocationWithExtraFields(location);
        }

        deltaTime = location.getTime() - lastLocation.getTime();
    
        if ((lastGoodLocation != null) && ((location.getTime() - lastGoodLocation.getTime()) < 1000)) {
            // less than 1000ms, skip this location
            return;
        }
        
        deltaDistance = location.distanceTo(lastLocation);

        if (location.getAccuracy() <= _minAccuracy) {

            if (firstGoodLocation == null) {
                firstGoodLocation = new LocationWithExtraFields(location);
            }  
            
            if (location.getAccuracy() <= (_minAccuracy / 1.5f)) {
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
            
            //Logger("localAverageSpeed:" + localAverageSpeed + " speed=" + location.getSpeed());
            
            // additional conditions to compute statistics
            if (
                  (_distance == 0) // 1st location
                ||
                  (localAverageSpeed > _minSpeedToComputeStats)
            ) {

                if (lastGoodAscentLocation == null) {
                    lastGoodAscentLocation = new LocationWithExtraFields(location);
                    lastGoodAscentRateLocation = new LocationWithExtraFields(location);
                }

                if (lastGoodAscentLocation != null && (
                    ((Math.abs(location.getAltitude() - lastGoodAscentLocation.getAltitude()) >= _minAltitudeChangeLevel1) && (location.getAccuracy() <= _minAccuracyForAltitudeChangeLevel1))
                        ||
                    ((Math.abs(location.getAltitude() - lastGoodAscentLocation.getAltitude()) >= _minAltitudeChangeLevel2) && (location.getAccuracy() <= _minAccuracyForAltitudeChangeLevel2))
                        ||
                    ((Math.abs(location.getAltitude() - lastGoodAscentLocation.getAltitude()) >= _minAltitudeChangeLevel3) && (location.getAccuracy() <= _minAccuracyForAltitudeChangeLevel3))

                )) {
                    
                    // compute ascent
                    // always remember that accuracy is 3x worth on altitude than on latitude/longitude
                    deltaAscent = Math.floor(location.getAltitude() - lastGoodAscentLocation.getAltitude());
                    
                    Logger("alt:" + lastGoodAscentLocation.getAltitude() + "->" + location.getAltitude() + ":" + deltaAscent + " - acc: " + location.getAccuracy());
                    
                    lastGoodAscentLocation = new LocationWithExtraFields(location);

                    // try to compute ascentRate if enough time has elapsed
                    long tmpDeltaTime = location.getTime() - lastGoodAscentRateLocation.getTime();
                    
                    if (tmpDeltaTime < _minDeltaTimeForAscentRate) {
                        // not enough time since lastGoodAscentRateLocation to compute ascentRate and slope
                        Logger("tmpDeltaTime:" + tmpDeltaTime +"<"+ _minDeltaTimeForAscentRate + " ascentRate skip");
                    } else {
                        
                        double tmpDeltaAscent = Math.floor(location.getAltitude() - lastGoodAscentRateLocation.getAltitude());
                        float tmpDeltaDistance = _distance - lastGoodAscentRateLocation.distance;
                        
                        _ascentRate = (float) tmpDeltaAscent / (tmpDeltaTime) * 1000; // m/s
                        
                        if (tmpDeltaDistance != 0) {
                            _slope = (float) tmpDeltaAscent / tmpDeltaDistance; // in %
                        } else {
                            _slope = 0;
                        }
                        
                        Logger("alt:" + lastGoodAscentRateLocation.getAltitude() + "->" + location.getAltitude() + ":" + tmpDeltaAscent + " _ascentRate:" + _ascentRate + " _slope:" + _slope);
                        
                        lastGoodAscentRateLocation = new LocationWithExtraFields(location);
                    }
                    
                }

                _elapsedTime += deltaTime;
                _distance += deltaDistance;
                if (deltaAscent > 0) {
                    _ascent += deltaAscent;
                }
                
                _averageSpeed = (float) _distance / ((float) _elapsedTime / 1000f);

                nbGoodLocations++;

                Logger(location.getTime()/1000+ " deltaDistance:" + deltaDistance + " deltaTime:" + deltaTime + " deltaAscent:" + deltaAscent + " _ascent:" + _ascent);
                Logger("_distance: " + _distance + " _averageSpeed: " + _averageSpeed + " _elapsedTime:" + _elapsedTime);

                // additional conditions to compute statistics
                if (
                      (_distance == 0) // 1st location
                    ||
                      (deltaTime >= _minDeltaTimeToSaveLocation)
                    ||
                      (deltaDistance >= _minDeltaDistanceToSaveLocation)
                ) {
                    // this Location could be saved
                    //Logger("save Location");

                    // TODO
                }
                
            } // additional conditions to compute statistics
            
            lastGoodLocation = new LocationWithExtraFields(location);
            
        } // if (location.getAccuracy() <= _minAccuracy) {
        
        lastLocation = new LocationWithExtraFields(location);
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
                Log.v("JayPS-" + TAG + ":" + level, s);
            }
        } else {
            if (this.debugLevel >= level) {
                Log.v("JayPS-" + TAG + ":" + level, s);
            }
        }
    }
}
