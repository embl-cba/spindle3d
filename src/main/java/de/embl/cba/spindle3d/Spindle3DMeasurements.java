package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.Logger;
import de.embl.cba.morphometry.Measurements;

import java.util.HashMap;
import java.util.Map;

public class Spindle3DMeasurements
{
	public static final String DNA_AXIAL_EXTEND = "DNA_Width";
	public static final String DNA_LATERAL_EXTEND = "DNA_Length";
	public static final String DNA_VOLUME = "DNA_Volume";
	public static final String SPINDLE_VOLUME = "Spindle_Volume";
	public static final String DNA_RELATIVE_CENTRAL_INTENSITY = "DNA_Normalised_Central_Intensity";
	public static final String DNA_SPINDLE_CENTER_DISTANCE = "Dna_Center_To_Spindle_Center_Distance";
	public static final String SPINDLE_AXIS_TO_COVERSLIP_PLANE_ANGLE_DEGREES = "Spindle_Axis_To_Coverslip_Plane_Angle_Degrees";
	public static final String LENGTH_UNIT = "um";
	public static final String VOLUME_UNIT = "um3";
	public static final int ALIGNED_DNA_AXIS = 2;
	public static final String COMMENT = "Comment";
	public static final String SPINDLE_POLE_REFINEMENT_DISTANCE = "Spindle_Pole_Refinement_Distance";
	public static final String SPINDLE_AXIAL_EXTEND = "Spindle_Length";
	public static final String SPINDLE_LATERAL_EXTEND = "Spindle_Width";
	public static final String ANALYSIS_INTERRUPTED_LOW_DYNAMIC_DNA =
			"Analysis interrupted: Too low dynamic range in DNA image";
	public static final String ANALYSIS_INTERRUPTED_LOW_DYNAMIC_TUBULIN =
			"Analysis interrupted: Too low dynamic range in tubulin image";
	public static final String ANALYSIS_FINISHED = "Analysis finished.";
	public static final String SEP = "_";
	public static final String SPINDLE_TUBULIN_COV = "Spindle_Tubulin_CoV";
	public static final String SPINDLE_TUBULIN_SUM = "Spindle_Tubulin_Sum";

	public Double dnaLateralExtend = Double.NaN;
	public Double dnaAxialExtend = Double.NaN;
	public Double dnaVolumeCalibrated = Double.NaN;
	public Double dnaRelativeCentralIntensity = Double.NaN;
	public Double spindleAxialExtend = Double.NaN;
	public Double spindlePoleARefinementDistance = Double.NaN;
	public Double spindlePoleBRefinementDistance = Double.NaN;
	public Double spindleThreshold = Double.NaN;
	public Double spindleSNR = Double.NaN;
	public Double spindleVolume = Double.NaN;
	public Double spindleWidthMin = Double.NaN;
	public Double spindleWidthMax = Double.NaN;
	public Double dnaCenterToSpindleCenterDistance = Double.NaN;
	public Double angleSpindleAxisToCoverslipPlaneInDegrees = Double.NaN;
	public Double dnaVolumeThreshold = Double.NaN;
	public String log = "";
	public Double dnaInitialThreshold = Double.NaN;
	public String version;
	public Double spindleCoefficientOfVariation = Double.NaN;
	public Double spindleSum = Double.NaN;
	public double tubulinInitialThreshold;

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

		addMeasurement( DNA_AXIAL_EXTEND + SEP + LENGTH_UNIT, dnaAxialExtend );

		addMeasurement( Spindle3DMeasurements.DNA_LATERAL_EXTEND + SEP + Spindle3DMeasurements.LENGTH_UNIT, dnaLateralExtend );

		addMeasurement( Spindle3DMeasurements.DNA_VOLUME + SEP + Spindle3DMeasurements.VOLUME_UNIT, dnaVolumeCalibrated );

		addMeasurement( Spindle3DMeasurements.DNA_RELATIVE_CENTRAL_INTENSITY, dnaRelativeCentralIntensity );

		addMeasurement( SPINDLE_POLE_REFINEMENT_DISTANCE + SEP + "PoleA" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleARefinementDistance );

		addMeasurement( SPINDLE_POLE_REFINEMENT_DISTANCE + SEP + "PoleB" + SEP + Spindle3DMeasurements.LENGTH_UNIT, spindlePoleBRefinementDistance );

		addMeasurement( getSpindleLengthKey(), spindleAxialExtend );

		addMeasurement( "Spindle_Intensity_Threshold",  spindleThreshold );

		addMeasurement( "Spindle_SNR",  spindleSNR );

		addMeasurement( getSpindleVolumeKey(), spindleVolume );

		addMeasurement( Spindle3DMeasurements.getSpindleWidthMinKey(), spindleWidthMin );

		addMeasurement( Spindle3DMeasurements.getSpindleWidthMaxKey(), spindleWidthMax );

		addMeasurement( Spindle3DMeasurements.DNA_SPINDLE_CENTER_DISTANCE + SEP + Spindle3DMeasurements.LENGTH_UNIT, dnaCenterToSpindleCenterDistance );

		addMeasurement( Spindle3DMeasurements.SPINDLE_AXIS_TO_COVERSLIP_PLANE_ANGLE_DEGREES, angleSpindleAxisToCoverslipPlaneInDegrees );

		addMeasurement( SPINDLE_TUBULIN_COV, spindleCoefficientOfVariation );

		addMeasurement( SPINDLE_TUBULIN_SUM, spindleSum );

		addMeasurement( COMMENT, log );
	}

	public static String getSpindleLengthKey()
	{
		return SPINDLE_AXIAL_EXTEND + SEP + Spindle3DMeasurements.LENGTH_UNIT;
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

	public static String getSpindleVolumeKey()
	{
		return Spindle3DMeasurements.SPINDLE_VOLUME + SEP + Spindle3DMeasurements.VOLUME_UNIT;
	}

	public static String getSpindleWidthMaxKey()
	{
		return "Spindle_Width_Max" + SEP + LENGTH_UNIT;
	}

	public static String getSpindleWidthMinKey()
	{
		return "Spindle_Width_Min" + SEP + LENGTH_UNIT;
	}

	@Deprecated
	public static String getSpindleWidthKey()
	{
		return SPINDLE_LATERAL_EXTEND + SEP + LENGTH_UNIT;
	}

	public static String getDnaLateralExtendKey()
	{
		return DNA_LATERAL_EXTEND
				+ SEP + LENGTH_UNIT;
	}
}
