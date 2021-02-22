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
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.script.ScriptService;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class Spindle3DCommand< R extends RealType< R > > implements Command
{
	public Spindle3DSettings settings = new Spindle3DSettings();

	@Parameter
	public OpService opService;

	@Parameter
	public ScriptService scriptService;

	@Parameter ( label = "Output Directory", style = "directory" )
	public File outputDirectory;

	@Parameter ( label = "DNA Channel [one-based index]" )
	public long dnaChannelIndexOneBased = 2;

	@Parameter ( label = "Spindle Channel [one-based index]" )
	public long spindleChannelIndexOneBased = 1;

	@Parameter( visibility = ItemVisibility.MESSAGE )
	public String version = "Spindle Morphometry Version: " + Spindle3DVersion.VERSION;

	public boolean showIntermediateImages = false;
	public boolean showIntermediatePlots = false;
	public boolean saveResults = true;

	protected File inputImageFilesParentDirectory = new File("/" );
	protected String imageName;
	protected HashMap< Integer, Map< String, Object > > objectMeasurements;

	protected void setSettings()
	{
		settings.showIntermediateImages = showIntermediateImages;
		settings.showIntermediatePlots = showIntermediatePlots;
		settings.outputDirectory = outputDirectory;
		settings.version = version;
		Logger.log( settings.toString() );
	}

	public HashMap< Integer, Map< String, Object > > getObjectMeasurements()
	{
		return objectMeasurements;
	}


	protected void processFile( String imagePath )
	{
		setImageName( new File( imagePath ).getName() );

		final ImagePlus imagePlus = Utils.openWithBioFormats( imagePath );
		imagePlus.setTitle( imageName );

		final RandomAccessibleInterval< BitType > cellMask = tryOpenCellMask( imagePath );

		processImagePlus( imagePlus, cellMask );
	}

	protected boolean fetchSettingAndInit()
	{
		DebugTools.setRootLevel( "OFF" ); // Bio-Formats
		if ( ! ImageSuite3D.isAvailable() ) return false;
		setSettings();
		return true;
	}

	protected void processImagePlus( ImagePlus imagePlus, RandomAccessibleInterval< BitType > cellMask )
	{
		setImageName( imagePlus.getTitle() );
		logStart( imageName );

		final RandomAccessibleInterval< R > raiXYCZ = asRAIXYCZ( imagePlus );

		Spindle3DMorphometry morphometry = new Spindle3DMorphometry( settings, opService, scriptService );
		morphometry.setCellMask( cellMask );
		final String log = morphometry.run( raiXYCZ );
		Logger.log( log );

		objectMeasurements = morphometry.getObjectMeasurements();

		addImagePathToMeasurements(
				inputImageFilesParentDirectory.toPath(),
				new File( imagePlus.getOriginalFileInfo().directory, imagePlus.getOriginalFileInfo().fileName ),
				objectMeasurements,
				"Path_InputImage" );

		if ( saveResults ) new File( getOutputDirectory() ).mkdirs();

		if ( log.equals( Spindle3DMeasurements.ANALYSIS_FINISHED ))
		{
			if ( this.settings.showOutputImage == true || saveResults )
			{
				final CompositeImage outputImage = morphometry.createOutputImage();

				if ( this.settings.showOutputImage == true )
					outputImage.show();

				if ( saveResults )
					saveOutputImageAndAddImagePathsToMeasurements( outputImage );
			}
		}

		if ( saveResults ) saveMeasurements( morphometry );

		logEnd();
	}

	protected RandomAccessibleInterval< R > asRAIXYCZ( ImagePlus imagePlus )
	{
		setSettingsFromImagePlus( imagePlus );

		final RandomAccessibleInterval< R > raiXYCZ = ImageJFunctions.wrapReal( imagePlus );

		settings.dnaChannelIndex = dnaChannelIndexOneBased - 1;
		settings.tubulinChannelIndex = spindleChannelIndexOneBased - 1;
		return raiXYCZ;
	}

	protected RandomAccessibleInterval< BitType > tryOpenCellMask( String imagePath )
	{
		final String cellMaskPath = imagePath.replace( ".tif", "_CellMask.tif" );

		final File cellMaskFile = new File( cellMaskPath );
		if ( cellMaskFile.exists() )
		{
			final RandomAccessibleInterval< R > rai = ImageJFunctions.wrapReal( Utils.openWithBioFormats( cellMaskPath ) );
			RandomAccessibleInterval< BitType > cellMask = Converters.convert( rai, ( i, o ) -> o.set( i.getRealDouble() > 0.5 ? true : false ), new BitType() );
			Logger.log( "Found cell mask file: " + cellMaskPath );
			return cellMask;
		}
		else
		{
			Logger.log( "Did not find cell mask file: " + cellMaskPath );
			return null;
		}
	}

	protected void setImageName( String name )
	{
		imageName = name.replace( ".tif", "" );
		imageName = imageName.replace( ".ome", "" );
		imageName = imageName.replace( ".zip", "" );
	}

	protected void logEnd()
	{
		Logger.log( "Done!" );
	}

	protected void logStart( String imageName )
	{
		Logger.log( "\n## Spindle Morphometry Measurements" );
		Logger.log( "Processing image: " + imageName );
	}

	protected void saveMeasurements( Spindle3DMorphometry morphometry )
	{
		final JTable jTable = Measurements.asTable( objectMeasurements );

		final File tableOutputFile = new File( getOutputDirectory() + "measurements.txt" );

		Logger.log( "Saving measurements table:\n" + tableOutputFile );

		Tables.saveTable( jTable, tableOutputFile );
	}

	protected String getOutputDirectory()
	{
		return outputDirectory
				+ File.separator
				+ imageName
				+ File.separator;
	}

	protected void setSettingsFromImagePlus( ImagePlus imagePlus )
	{
		settings.inputCalibration = Utils.getCalibration( imagePlus );
		settings.imagePlusCalibration = imagePlus.getCalibration();
		settings.inputDataSetName = imagePlus.getTitle();
	}

	protected void saveOutputImageAndAddImagePathsToMeasurements( ImagePlus imagePlus )
	{
		final Path parentPath = inputImageFilesParentDirectory.toPath();

		final File outputImageFile = new File( getOutputDirectory() + imageName + "-out.zip" );

		addImagePathToMeasurements( parentPath, outputImageFile, objectMeasurements, "Path_OutputImage" );

		Logger.log( "Saving output image:\n" + outputImageFile );
		IJ.saveAs( imagePlus, "ZIP", outputImageFile.toString() );
	}

	protected static void addImagePathToMeasurements(
			Path parentPath,
			File inputImageFile,
			HashMap< Integer, Map< String, Object > > objectMeasurements,
			String path_inputImage )
	{
		Path relativeInputImagePath;
		try
		{
			relativeInputImagePath = parentPath.relativize( inputImageFile.toPath() );
		}
		catch ( Exception e )
		{
			Logger.log( "[WARNING]: Could not relativize path " + inputImageFile );
			relativeInputImagePath = inputImageFile.toPath();
		}

		Measurements.addMeasurement(
				objectMeasurements,
				0,
				path_inputImage,
				relativeInputImagePath );
	}
}
