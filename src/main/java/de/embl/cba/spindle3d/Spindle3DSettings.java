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
	public double voxelSizeForAnalysis = 0.25; // um
	public double maxMetaphasePlateLength = 22; // um
	public double maxMetaphasePlateWidth = 8; // um
	public double metaphasePlateWidthDerivativeDelta = 1.0; // um
	public double metaphasePlateLengthDerivativeDelta = 2.0; // um
	public double spindleFragmentInclusionZone = 3.0; // um;
	public double axialPoleRefinementRadius = 1.0; // um
	public double lateralPoleRefinementRadius = 2.0; // um
	public double voxelSizeForInitialDNAThreshold = 1.5; // um

	@Deprecated // ??
	public double minimalDnaAndTubulinFragmentsVolume = 1 * 3 * 3; // um^3

	public double maxCentralObjectRegionsDistance = 7; // um

	public double cellRadius = 6.0; // um
	public double erosionOfDnaMaskInCalibratedUnits = 1.0; // um
	public double interestPointsRadius = 0.5; // um

	/**
	 * Intensity
	 */
	public double initialThresholdFactor = 0.5;
	public int minimalDynamicRange = 7;

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
	public double spindleThresholdFactor = 1.0;
	public File roiDetectionMacro;

	public boolean smoothSpindle = false; // TODO PAPER Remove?

	// TODO PAPER Clean this up and make sure everything is added
	public String toString()
	{
		String settings = new String();

		settings += "\n";
		settings += "## Spindle Morphometry Settings\n";
		settings += "voxelSizeForAnalysis: " + voxelSizeForAnalysis + "\n";
		settings += "voxelSizeForInitialDNAThreshold: " + voxelSizeForInitialDNAThreshold + "\n";
		settings += "dnaThresholdFactor: " + initialThresholdFactor + "\n";
		settings += "spindleThresholdFactor: " + spindleThresholdFactor + "\n";
		settings += "spindleFragmentInclusionZone: " + spindleFragmentInclusionZone + "\n";

		settings += "minimalDynamicRange: " + minimalDynamicRange + "\n";
		settings += "metaphasePlateWidthDerivativeDelta: " + metaphasePlateWidthDerivativeDelta + "\n";
		settings += "metaphasePlateLengthDerivativeDelta: " + metaphasePlateLengthDerivativeDelta + "\n";

		settings += "maxCentralObjectRegionsDistance: " + maxCentralObjectRegionsDistance + "\n";
		settings += "erosionOfDnaMaskInCalibratedUnits: " + erosionOfDnaMaskInCalibratedUnits + "\n";

		return settings;
	}

}
