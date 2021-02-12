package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.ImageSuite3D;
import de.embl.cba.morphometry.Logger;
import de.embl.cba.morphometry.Utils;
import de.embl.cba.morphometry.Measurements;
import de.embl.cba.tables.Tables;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static de.embl.cba.spindle3d.Spindle3DSettings.CCDM_NONE;
import static de.embl.cba.spindle3d.Spindle3DSettings.CellCenterDetectionMethod;
import static de.embl.cba.spindle3d.Spindle3DVersion.VERSION;


@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D Advanced..." )
public class Spindle3DAdvancedCommand< R extends RealType< R > > implements Command
{
	public Spindle3DSettings settings = new Spindle3DSettings();

	@Parameter
	private Context context;

	@Parameter
	public ScriptService scriptService;

	@Parameter
	public OpService opService;

	@Parameter ( label = "Input Image File" )
	public File inputImageFile;

	// TODO: how to make this more clear and easy?
	// (it is to remove to part of the path to only store a relative directory )
	@Parameter ( label = "Input Image Files Parent Directory", style = "directory" )
	public File inputImageFilesParentDirectory = new File("/" );

	@Parameter ( label = "Output Directory", style = "directory" )
	public File outputDirectory;

	@Parameter ( label = "Voxel size for analysis" )
	public double voxelSizeForAnalysis = settings.voxelSizeForAnalysis;

//	@Parameter ( label = "DNA threshold factor" )
	public double dnaThresholdFactor = settings.initialThresholdFactor;

	@Parameter ( label = "Minimum Dynamic Range [segmentation threshold gray value]" )
	public int minimalDynamicRange = settings.minimalDynamicRange;

	@Parameter ( label = "Maximal metaphase plate length [um]" )
	public double maxMetaphasePlateLength = settings.maxMetaphasePlateLength;

	@Parameter ( label = "Axial Spindle Poles Refinement Search Radius [um]" )
	public double axialPoleRefinementRadius = settings.axialPoleRefinementRadius;

	@Parameter ( label = "Lateral Spindle Poles Refinement Search Radius [um]" )
	public double lateralPoleRefinementRadius = settings.lateralPoleRefinementRadius;

	@Parameter ( label = "TEST: Smooth Spindle" )
	public boolean smoothSpindle = settings.smoothSpindle;

	@Parameter ( label = "DNA Channel [one-based index]" )
	public long dnaChannelIndexOneBased = 2;

	@Parameter ( label = "Spindle Channel [one-based index]" )
	public long spindleChannelIndexOneBased = 1;

	@Parameter ( label = "Show Intermediate Images" )
	public boolean showIntermediateImages = false;

	@Parameter ( label = "Show Intermediate Plots" )
	public boolean showIntermediatePlots = false;

	@Parameter ( label = "ROI detection macro", required = false )
	public File macroFile;

	// TODO
//	@Parameter ( label = "Initial Cell Center Detection Method", choices = { CCDM_NONE, CCDM_DNA, CCDM_TUBULIN } )
	public String cellCenterDetectionMethodChoice = CCDM_NONE;

	//	@Parameter ( label = "Use CATS for Metaphase Detection" )
	public boolean useCATS = false;

//	@Parameter ( label = "CATS Classifier" )
	public File classifier;

	@Parameter( visibility = ItemVisibility.MESSAGE )
	private String version = "Spindle Morphometry Version: " + VERSION;

//	@Parameter( type = ItemIO.OUTPUT )
//	private double spindleVolume;

	public boolean saveResults = true;

	private String imageName;
	private HashMap< Integer, Map< String, Object > > objectMeasurements;

	public void run()
	{
		if ( ! ImageSuite3D.isAvailable() ) return;
		setSettingsFromUI();
		processFile( inputImageFile );
	}

	private void setSettingsFromUI()
	{
		settings.smoothSpindle = smoothSpindle; // TODO PAPER : Remove
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
		settings.useCATS = useCATS;
		settings.classifier  = classifier;
		settings.cellCenterDetectionMethod = CellCenterDetectionMethod.valueOf( cellCenterDetectionMethodChoice );
		settings.roiDetectionMacro = macroFile;

		Logger.log( settings.toString() );
	}

	public HashMap< Integer, Map< String, Object > > getObjectMeasurements()
	{
		return objectMeasurements;
	}

	private void processFile( File file )
	{
		setImageName();

		logStart();

		final ImagePlus imagePlus = Utils.openWithBioFormats( file.toString() );
		setSettingsFromImagePlus( imagePlus );

		final RandomAccessibleInterval< R > raiXYCZ = ImageJFunctions.wrapReal( imagePlus );

		settings.dnaChannelIndex = dnaChannelIndexOneBased - 1;
		settings.tubulinChannelIndex = spindleChannelIndexOneBased - 1;

		//final OpService service = context.service( OpService.class );
		Spindle3DMorphometry morphometry = new Spindle3DMorphometry( settings, opService, scriptService );
		final String log = morphometry.run( raiXYCZ );
		Logger.log( log );

		final Spindle3DMeasurements measurements =
				morphometry.getMeasurements();

		//spindleVolume = measurements.spindleVolume;

		objectMeasurements = morphometry.getObjectMeasurements();

		addImagePathToMeasurements(
				inputImageFilesParentDirectory.toPath(),
				inputImageFile,
				objectMeasurements,
				"Path_InputImage" );

		if ( saveResults ) new File( getOutputDirectory() ).mkdirs();

		if ( log.equals( Spindle3DMeasurements.ANALYSIS_FINISHED ))
		{
			if ( settings.showOutputImage == true || saveResults )
			{
				final CompositeImage outputImage = morphometry.createOutputImage();

				if ( settings.showOutputImage == true )
					outputImage.show();

				if ( saveResults )
					saveOutputImageAndAddImagePathsToMeasurements( outputImage );
			}
		}

		if ( saveResults ) saveMeasurements( morphometry );

		logEnd();
	}

	private void setImageName()
	{
		imageName = inputImageFile.getName().replace( ".tif", "" );
		imageName = inputImageFile.getName().replace( ".ome", "" );
		imageName = inputImageFile.getName().replace( ".zip", "" );
	}

	private void logEnd()
	{
		Logger.log( "Done!" );
	}

	private void logStart()
	{
		Logger.log( "## Spindle Morphometry Measurement" );
		Logger.log( "Processing file " + imageName );
	}

	private void saveMeasurements( Spindle3DMorphometry morphometry )
	{
		final JTable jTable = Measurements.asTable( objectMeasurements );

		final File tableOutputFile = new File( getOutputDirectory() + "measurements.txt" );

		Logger.log( "Saving:\n" + tableOutputFile );

		Tables.saveTable( jTable, tableOutputFile );
	}

	private String getOutputDirectory()
	{
		return outputDirectory
				+ File.separator
				+ imageName
				+ File.separator;
	}

	private void setSettingsFromImagePlus( ImagePlus imagePlus )
	{
		settings.inputCalibration = Utils.getCalibration( imagePlus );
		settings.imagePlusCalibration = imagePlus.getCalibration();
		settings.inputDataSetName = imagePlus.getTitle();
	}

	private void saveOutputImageAndAddImagePathsToMeasurements( ImagePlus imagePlus )
	{
		final Path parentPath = inputImageFilesParentDirectory.toPath();

		final File outputImageFile = new File( getOutputDirectory() + imageName + "-out.zip" );

		addImagePathToMeasurements( parentPath, outputImageFile, objectMeasurements, "Path_OutputImage" );

		Logger.log( "Saving:\n" + outputImageFile );
		IJ.saveAs( imagePlus, "ZIP", outputImageFile.toString() );
	}

	private static void addImagePathToMeasurements(
			Path parentPath,
			File inputImageFile,
			HashMap< Integer, Map< String, Object > > objectMeasurements,
			String path_inputImage )
	{
		final Path relativeInputImagePath = parentPath.relativize( inputImageFile.toPath() );

		Measurements.addMeasurement(
				objectMeasurements,
				0,
				path_inputImage,
				relativeInputImagePath );
	}

}
