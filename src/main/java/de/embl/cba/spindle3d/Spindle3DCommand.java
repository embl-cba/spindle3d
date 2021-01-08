package de.embl.cba.spindle3d;

import de.embl.cba.morphometry.ImageSuite3D;
import de.embl.cba.morphometry.Logger;
import de.embl.cba.morphometry.Measurements;
import de.embl.cba.morphometry.Utils;
import de.embl.cba.tables.Tables;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import loci.common.DebugTools;
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

@Plugin(type = Command.class, menuPath = "Plugins>Spindle3D>Spindle3D..." )
public class Spindle3DCommand< R extends RealType< R > > implements Command
{
	public Spindle3DSettings settings = new Spindle3DSettings();

	@Parameter
	private Context context;

	@Parameter
	public OpService opService;

	@Parameter
	public ScriptService scriptService;

	@Parameter ( label = "Input Image File" )
	public File inputImageFile;

	@Parameter ( label = "Output Directory", style = "directory" )
	public File outputDirectory;

	@Parameter ( label = "DNA Channel [one-based index]" )
	public long dnaChannelIndexOneBased = 2;

	@Parameter ( label = "Spindle Channel [one-based index]" )
	public long spindleChannelIndexOneBased = 1;

	public boolean showIntermediateImages = false;

	public boolean showIntermediatePlots = false;


	@Parameter( visibility = ItemVisibility.MESSAGE )
	private String version = "Spindle Morphometry Version: " + Spindle3DVersion.VERSION;




//	@Parameter( type = ItemIO.OUTPUT )
//	private double spindleVolume;

	private String cellCenterDetectionMethodChoice = CCDM_NONE;
	private boolean useCATS = false;
	private File classifier;
	private double voxelSpacingDuringAnalysis = settings.workingVoxelSize;
	private double dnaThresholdFactor = settings.initialThresholdFactor;
	private int minimalDynamicRange = settings.minimalDynamicRange;
	public boolean saveResults = true;
	private File inputImageFilesParentDirectory = new File("/" );

	private String imageName;
	private HashMap< Integer, Map< String, Object > > objectMeasurements;

	public void run()
	{
		DebugTools.setRootLevel("OFF"); // Bio-Formats
		if ( ! ImageSuite3D.isAvailable() ) return;
		setSettingsFromUI();
		processFile( inputImageFile );
	}

	private void setSettingsFromUI()
	{
		settings.showIntermediateImages = showIntermediateImages;
		settings.showIntermediatePlots = showIntermediatePlots;
		settings.workingVoxelSize = voxelSpacingDuringAnalysis;
		settings.outputDirectory = outputDirectory;
		settings.initialThresholdFactor = dnaThresholdFactor;
		settings.minimalDynamicRange = minimalDynamicRange;
		settings.version = version;
		settings.useCATS = useCATS;
		settings.classifier  = classifier;
		settings.cellCenterDetectionMethod = Spindle3DSettings.CellCenterDetectionMethod.valueOf( cellCenterDetectionMethodChoice );

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
