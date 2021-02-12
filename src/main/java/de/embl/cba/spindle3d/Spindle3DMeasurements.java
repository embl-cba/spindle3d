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

	public static final String ANALYSIS_INTERRUPTED_LOW_DYNAMIC_DNA =
			"Analysis interrupted: Too low dynamic range in DNA image";
	public static final String ANALYSIS_INTERRUPTED_LOW_DYNAMIC_TUBULIN =
			"Analysis interrupted: Too low dynamic range in tubulin image";
	public static final String ANALYSIS_FINISHED = "Analysis finished.";

	public Double metaphasePlateLength = Double.NaN;
	public Double metaphasePlateWidth = Double.NaN;
	public Double chormatinVolume = Double.NaN;
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
	public Double spindleSumIntensityCorrected = Double.NaN;
	public Double spindleSumIntensityRaw = Double.NaN;;
	public Double spindleWidthAvg = Double.NaN;
	public Double spindleAspectRatio = Double.NaN;

	private HashMap< Integer, Map< String, Object > > objectMeasurements;

	public Spindle3DMeasurements( HashMap< Integer, Map< String, Object > > objectMeasurements )
	{
		this.objectMeasurements = objectMeasurements;
	}

	public void setObjectMeasurements( )
	{
		addMeasurement( "Version", version );

		addMeasurement( "DNA_Initial_Threshold", dnaInitialThreshold );

		addMeasurement( "DNA_Volume_Threshold", dnaVolumeThreshold );

		addMeasurement( addLengthUnit( "MetaphasePlate_Width" ), metaphasePlateWidth );

		addMeasurement( addLengthUnit( "MetaphasePlate_Length" ), metaphasePlateLength );

		addMeasurement( "Chromatin_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, chormatinVolume );

		addMeasurement( "Chromatin_Dilation", chromatinDilation );

		addMeasurement( "Spindle_Pole_Refinement_Distance" + SEP + "PoleA" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleARefinementDistance );

		addMeasurement( "Spindle_Pole_Refinement_Distance" + SEP + "PoleB" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleBRefinementDistance );

		addMeasurement( "Spindle_Intensity_Threshold",  spindleThreshold );

		addMeasurement( "Spindle_SNR", spindleSNR );

		addMeasurement( "Spindle_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, spindleVolume );

		addMeasurement( addLengthUnit( "Spindle_Length" ), spindleLength );

		addMeasurement( addLengthUnit( "Spindle_Width_Min" ), spindleWidthMin );

		addMeasurement( addLengthUnit( "Spindle_Width_Max" ), spindleWidthMax );

		addMeasurement( addLengthUnit( "Spindle_Width_Avg" ), spindleWidthAvg );

		addMeasurement( "Spindle_Aspect_Ratio", spindleAspectRatio );

		addMeasurement( addLengthUnit( "Spindle_Center_To_MetaphasePlate_Center_Distance" ), spindleCenterToMetaphasePlateCenterDistance );

		addMeasurement( "Spindle_Angle_Degrees", spindleAngle );

		addMeasurement( "Spindle_Intensity_Variation", spindleIntensityVariation );

		addMeasurement( "Spindle_Sum_Intensity_Corrected", spindleSumIntensityCorrected );

		addMeasurement( "Spindle_Sum_Intensity_Raw", spindleSumIntensityRaw );

		addMeasurement( "Comment", log );
	}

	private void addMeasurement( String name, Object value )
	{
		Logger.log( name + ": " + value  );

		Measurements.addMeasurement(
				objectMeasurements,
				0,
				name,
				value );
	}

	public static String addLengthUnit( String name )
	{
		return name + SEP + LENGTH_UNIT;
	}
}
