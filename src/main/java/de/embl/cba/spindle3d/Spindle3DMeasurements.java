package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.Logger;
import de.embl.cba.morphometry.Measurements;

import java.util.HashMap;
import java.util.Map;

public class Spindle3DMeasurements
{
	public static final String LENGTH_UNIT = "um";
	public static final String VOLUME_UNIT = "um3";
	public static final String SEP = "_";

	public static final int ALIGNED_DNA_AXIS = 2;
	public static final String ANALYSIS_FINISHED = "Analysis finished.";

	// define constants to be accessible in the tests
	public static final String SPINDLE_LENGTH = addLengthUnit( "Spindle_Length" );
	public static final String SPINDLE_WIDTH_AVG = addLengthUnit( "Spindle_Width_Avg" );
	public static final String SPINDLE_ANGLE_DEGREES = "Spindle_Angle_Degrees";

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
	public Double dnaVolumeThreshold = Double.NaN;
	public String log = "";
	public Double dnaInitialThreshold = Double.NaN;
	public String version;
	public Double spindleIntensityVariation = Double.NaN;
	public Double spindleTubulinAverageIntensity = Double.NaN;
	public Double spindleSumIntensityRaw = Double.NaN;;
	public Double spindleWidthAvg = Double.NaN;
	public Double spindleAspectRatio = Double.NaN;
	public Double cellVolume = Double.NaN;
	public Double cellularTubulinAverageIntensity = Double.NaN;
	public Double cellVoxels = Double.NaN;
	public Double spindleVoxels = Double.NaN;

	private HashMap< Integer, Map< String, Object > > objectMeasurements;

	public Spindle3DMeasurements( HashMap< Integer, Map< String, Object > > objectMeasurements )
	{
		this.objectMeasurements = objectMeasurements;
	}

	public void setObjectMeasurements( )
	{
		add( "Version", version );

		add( "DNA_Initial_Threshold", dnaInitialThreshold );

		add( "DNA_Volume_Threshold", dnaVolumeThreshold );

		add( addLengthUnit( "MetaphasePlate_Width" ), metaphasePlateWidth );

		add( addLengthUnit( "MetaphasePlate_Length" ), metaphasePlateLength );

		add( addVolumeUnit( "Chromatin_Volume" ), chromatinVolume );

		add( "Chromatin_Dilation", chromatinDilation );

		add( "Spindle_Pole_Refinement_Distance" + SEP + "PoleA" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleARefinementDistance );

		add( "Spindle_Pole_Refinement_Distance" + SEP + "PoleB" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleBRefinementDistance );

		add( "Spindle_Intensity_Threshold",  spindleThreshold );

		add( "Spindle_SNR", spindleSNR );

		add( "Spindle_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, spindleVolume );

		add( SPINDLE_LENGTH, spindleLength );

		add( addLengthUnit( "Spindle_Width_Min" ), spindleWidthMin );

		add( addLengthUnit( "Spindle_Width_Max" ), spindleWidthMax );

		add( SPINDLE_WIDTH_AVG, spindleWidthAvg );

		add( "Spindle_Aspect_Ratio", spindleAspectRatio );

		add( addLengthUnit( "Spindle_Center_To_MetaphasePlate_Center_Distance" ), spindleCenterToMetaphasePlateCenterDistance );

		add( SPINDLE_ANGLE_DEGREES, spindleAngle );

		add( "Spindle_Intensity_Variation", spindleIntensityVariation );

		add( "Spindle_Sum_Intensity_Corrected", spindleTubulinAverageIntensity );

		add( "Spindle_Sum_Intensity_Raw", spindleSumIntensityRaw );

		add( "Cell_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, cellVolume );

		add( "Cell_Tubulin_Sum_Intensity_Raw", cellularTubulinAverageIntensity );

		add( "Comment", log );
	}

	private void add( String name, Object value )
	{
		Logger.log( name + ": " + value  );
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
}
