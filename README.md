Advanced Location
======

An android class that helps you compute advanced data for GPS Location (average speed, distance, elapsed time, positive ascent).

Licensed under [MIT License](http://opensource.org/licenses/MIT)

Current version: 0.1

## Build Instructions:  
`./gradlew build`  

## To include in an android project

### In your project's settings.gradle file add...  
```
project(':advancedlocation-library').projectDir = new File('submodules/AdvancedLocation/advancedlocation-library')  
```

### Then add the library as dependency in your build.gradle
```
dependencies {
  compile project(':advancedlocation-library')
}
```
