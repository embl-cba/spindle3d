package de.embl.cba.spindle3d;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.embl.cba.morphometry.Logger;
import de.embl.cba.morphometry.Measurements;

import java.util.HashMap;
import java.util.Map;

public class Spindle3DMeasurements
{
	public static transient final String LENGTH_UNIT = "um";
	public static transient final String VOLUME_UNIT = "um3";
	public static transient final String SEP = "_";

	public static transient final int ALIGNED_DNA_AXIS = 2;
	public static transient final String ANALYSIS_FINISHED = "Analysis finished.";

	// define constants to be accessible in the tests
	public static transient final String SPINDLE_LENGTH = addLengthUnit( "Spindle_Length" );
	public static transient final String SPINDLE_WIDTH_AVG = addLengthUnit( "Spindle_Width_Avg" );
	public static transient final String SPINDLE_ANGLE_DEGREES = "Spindle_Angle_Degrees";

	public String version;
	public Double dnaVolumeThreshold = Double.NaN;
	public Double dnaInitialThreshold = Double.NaN;
	public Double metaphasePlateLength = Double.NaN;
	public Double metaphasePlateWidth = Double.NaN;
	public Double chromatinVolume = Double.NaN;
	public Double chromatinDilation = Double.NaN;
	public Double spindleLength = Double.NaN;
	public Double spindlePoleARefinementDistance = Double.NaN;
	public Double spindlePoleBRefinementDistance = Double.NaN;
	public Double spindleThreshold = Double.NaN;
	public Double spindleSNR = Double.NaN;
	public Double spindleVolume = Double.NaN;
	public Double spindleWidthMin = Double.NaN;
	public Double spindleWidthMax = Double.NaN;
	public Double spindleCenterToMetaphasePlateCenterDistance = Double.NaN;
	public Double spindleAngle = Double.NaN;
	public Double tubulinSpindleIntensityVariation = Double.NaN;
	public Double tubulinSpindleAverageIntensity = Double.NaN;
	public Double tubulinCellularAverageIntensity = Double.NaN;
	public Double tublinCytoplasmAverageIntensity = Double.NaN;
	public Double spindleWidthAvg = Double.NaN;
	public Double spindleAspectRatio = Double.NaN;
	public Double cellVolume = Double.NaN;
	public String log = "";

	private transient HashMap< Integer, Map< String, Object > > objectMeasurements;

	public Spindle3DMeasurements( HashMap< Integer, Map< String, Object > > objectMeasurements )
	{
		this.objectMeasurements = objectMeasurements;
	}

	public void setMeasurementsForExport( )
	{
		Logger.log( this.toString() );

		add( "Version", version );

		add( "DNA_Initial_Threshold", dnaInitialThreshold );

		add( "DNA_Volume_Threshold", dnaVolumeThreshold );

		add( addLengthUnit( "MetaphasePlate_Width" ), metaphasePlateWidth );

		add( addLengthUnit( "MetaphasePlate_Length" ), metaphasePlateLength );

		add( addVolumeUnit( "Chromatin_Volume" ), chromatinVolume );

		add( "Chromatin_Dilation", chromatinDilation );

		add( "Spindle_Pole_Refinement_Distance" + SEP + "PoleA" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleARefinementDistance );

		add( "Spindle_Pole_Refinement_Distance" + SEP + "PoleB" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleBRefinementDistance );

		add( "Tubulin_Spindle_Intensity_Threshold",  spindleThreshold );

		add( "Tubulin_Spindle_Intensity_Variation", tubulinSpindleIntensityVariation );

		add( "Tubulin_Spindle_Average_Intensity", tubulinSpindleAverageIntensity );

		add( "Tubulin_Cellular_Avg_Intensity", tubulinCellularAverageIntensity );

		add( "Spindle_SNR", spindleSNR );

		add( "Spindle_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, spindleVolume );

		add( SPINDLE_LENGTH, spindleLength );

		add( addLengthUnit( "Spindle_Width_Min" ), spindleWidthMin );

		add( addLengthUnit( "Spindle_Width_Max" ), spindleWidthMax );

		add( SPINDLE_WIDTH_AVG, spindleWidthAvg );

		add( "Spindle_Aspect_Ratio", spindleAspectRatio );

		add( addLengthUnit( "Spindle_Center_To_MetaphasePlate_Center_Distance" ), spindleCenterToMetaphasePlateCenterDistance );

		add( SPINDLE_ANGLE_DEGREES, spindleAngle );

		add( "Cell_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, cellVolume );

		add( "Comment", log );
	}

	private void add( String name, Object value )
	{
		Measurements.addMeasurement( objectMeasurements, 0, name, value );
	}

	public static String addLengthUnit( String name )
	{
		return name + SEP + LENGTH_UNIT;
	}

	public static String addVolumeUnit( String name )
	{
		return name + SEP + VOLUME_UNIT;
	}

	public String toString()
	{
		String s = "\n## Spindle3D Measurements\n";
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
		s += gson.toJson( this );
		s += "\n";
		return s;
	}
}
