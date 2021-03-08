package de.embl.cba.spindle3d.command;

import de.embl.cba.morphometry.Logger;
import de.embl.cba.spindle3d.Spindle3DCommand;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;


@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Process File (Advanced)..." )
public class Spindle3DAdvancedCommand< R extends RealType< R > > extends Spindle3DCommand< R >
{
	@Parameter ( label = "Input Image File" )
	public File inputImageFile;

	@Parameter ( label = "Voxel size for analysis" )
	public double voxelSizeForAnalysis = settings.voxelSizeForAnalysis;

	@Parameter ( label = "DNA threshold factor" )
	public double dnaThresholdFactor = settings.initialThresholdFactor;

	@Parameter ( label = "Minimum dynamic range [gray value]" )
	public int minimalDynamicRange = settings.minimalDynamicRange;

	@Parameter ( label = "Maximal metaphase plate length [um]" )
	public double maxMetaphasePlateLength = settings.maxMetaphasePlateLength;

	@Parameter ( label = "Axial spindle poles refinement search radius [um]" )
	public double axialPoleRefinementRadius = settings.axialPoleRefinementRadius;

	@Parameter ( label = "Lateral spindle poles refinement search radius [um]" )
	public double lateralPoleRefinementRadius = settings.lateralPoleRefinementRadius;

	@Parameter ( label = "Smooth spindle" )
	public boolean smoothSpindle = settings.smoothSpindle;

	@Parameter ( label = "Show intermediate images" )
	public boolean showIntermediateImages = false;

	@Parameter ( label = "Show intermediate plots" )
	public boolean showIntermediatePlots = false;

//	@Parameter ( label = "Cell ROI detection macro file (optional)", required = false )
	public File macroFile;

	public boolean saveResults = true;

	@Override
	public void run()
	{
		if ( ! fetchSettingAndInit() ) return;
		processFile( inputImageFile.toString() );
	}

	@Override
	protected void setSettings()
	{
		settings.smoothSpindle = smoothSpindle;
		settings.showIntermediateImages = showIntermediateImages;
		settings.showIntermediatePlots = showIntermediatePlots;
		settings.voxelSizeForAnalysis = voxelSizeForAnalysis;
		settings.maxMetaphasePlateLength = maxMetaphasePlateLength;
		settings.axialPoleRefinementRadius = axialPoleRefinementRadius;
		settings.lateralPoleRefinementRadius = lateralPoleRefinementRadius;
		settings.outputDirectory = outputDirectory;
		settings.initialThresholdFactor = dnaThresholdFactor;
		settings.minimalDynamicRange = minimalDynamicRange;
		settings.version = version;
		settings.roiDetectionMacro = macroFile;

		Logger.log( settings.toString() );
	}
}
