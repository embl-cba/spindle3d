package de.embl.cba.spindle3d;

import ij.measure.Calibration;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.io.File;

public class Spindle3DSettings<T extends RealType<T> & NativeType< T > >
{
	/**
	 * Spatial
	 */
	// TODO: make all micrometer everything relative to something
	public double workingVoxelSize = 0.25; // um
	public double maxDnaLateralExtend = 12; // um
	public double minimalDnaAndTubulinFragmentsVolume = 1 * 3 * 3; // um^3
	public double maxCentralObjectRegionsDistance = 7; // um
	public double cellRadius = 6.0; // um
	public double erosionOfDnaMaskInCalibratedUnits = 1.0; // um
	public double axialPoleRefinementRadius = 1.0; // um
	public double lateralPoleRefinementRadius = 2.0; // um
	public double spindleDerivativeDelta = 1.0; // um
	public double derivativeDelta = 3.0;
	public double interestPointsRadius = 0.5; // um

	/**
	 * Intensity
	 */
	public double initialThresholdFactor = 0.5;
	public double initialThresholdResolution = 1.5;
	public int minimalDynamicRange = 7;
	public double thresholdInUnitsOfBackgroundPeakHalfWidth = 5.0;


	/**
	 * Other
	 */
	public String version;
	public boolean showIntermediateImages = false;
	public boolean showIntermediatePlots = false;
	public double[] inputCalibration;
	public File outputDirectory;
	public String inputDataSetName;
	public Calibration imagePlusCalibration;
	public long dnaChannelIndex;
	public long tubulinChannelIndex;
	public boolean showOutputImage = false;
	public boolean showMetaphaseClassification = false;
	public boolean useCATS = false;
	public File classifier;
	public CellCenterDetectionMethod cellCenterDetectionMethod;
	public double spindleThresholdFactor = 1.0;
	public File roiDetectionMacro;

	public enum CellCenterDetectionMethod
	{
		None,
		BlurredDnaImage,
		BlurredTubulinImage
	}

	public static final String CCDM_NONE = "None";
	public static final String CCDM_DNA = "BlurredDnaImage";
	public static final String CCDM_TUBULIN = "BlurredTubulinImage";

	public String toString()
	{
		String settings = new String();

		settings += "\n";
		settings += "## Spindle Morphometry Settings\n";
		settings += "workingVoxelSize: " + workingVoxelSize + "\n";
		settings += "dnaThresholdResolution: " + initialThresholdResolution + "\n";
		settings += "dnaThresholdFactor: " + initialThresholdFactor + "\n";
		settings += "spindleThresholdFactor: " + spindleThresholdFactor + "\n";
		settings += "spindleDerivativeDelta: " + spindleDerivativeDelta + "\n";
		settings += "minimalDynamicRange: " + minimalDynamicRange + "\n";
		settings += "minimalDnaFragmentsVolume: " + minimalDnaAndTubulinFragmentsVolume + "\n";
		settings += "maxCentralObjectRegionsDistance: " + maxCentralObjectRegionsDistance + "\n";
		settings += "erosionOfDnaMaskInCalibratedUnits: " + erosionOfDnaMaskInCalibratedUnits + "\n";

		return settings;
	}

}
