package de.embl.cba.spindle3d;

import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import de.embl.cba.morphometry.*;
import de.embl.cba.morphometry.geometry.CoordinatesAndValues;
import de.embl.cba.morphometry.geometry.CurveAnalysis;
import de.embl.cba.morphometry.geometry.ellipsoids.EllipsoidVectors;
import de.embl.cba.morphometry.geometry.ellipsoids.Ellipsoids3DImageSuite;
import de.embl.cba.morphometry.regions.Regions;
import de.embl.cba.neighborhood.RectangleShape2;
import de.embl.cba.transforms.utils.Scalings;
import de.embl.cba.transforms.utils.Transforms;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import net.imagej.ops.OpService;
import net.imglib2.*;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.*;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.script.ScriptService;
import java.util.*;

import static de.embl.cba.morphometry.Angles.angleOfSpindleAxisToXAxisInRadians;
import static de.embl.cba.morphometry.viewing.BdvViewer.show;
import static de.embl.cba.transforms.utils.Scalings.createRescaledArrayImg;
import static de.embl.cba.transforms.utils.Transforms.getScalingFactors;

public class Spindle3DMorphometry< R extends RealType< R > & NativeType< R > >
{
	final Spindle3DSettings< R > settings;
	final OpService opService;
	private final ScriptService scriptService;

	private HashMap< Integer, Map< String, Object > > objectMeasurements;
	private AffineTransform3D rescaledToDnaAlignmentTransform;

	private RandomAccessibleInterval< R > dna;
	private RandomAccessibleInterval< R > tubulin;
	private RandomAccessibleInterval< R > dnaAlignedDna;
	private RandomAccessibleInterval< R > dnaAlignedTubulin;
	private RandomAccessibleInterval< R > spindleAlignedDna;
	private RandomAccessibleInterval< R > spindleAlignedTublin;

	private RandomAccessibleInterval< BitType > dnaAlignedInitialDnaMask;
	private RandomAccessibleInterval< BitType > dnaAlignedDnaMask;
	private RandomAccessibleInterval< BitType > spindleAlignedSpindleMask;
	private RandomAccessibleInterval< BitType > spindleAlignedDnaMask;
	private RandomAccessibleInterval< BitType > dnaAlignedSpindleMask;
	private RandomAccessibleInterval< BitType > cellMask;
	private RandomAccessibleInterval< BitType > dnaAlignedCellMask;
	private RandomAccessibleInterval< BitType > spindleAlignedCellMask;

	private ArrayList< double[] > dnaAlignedSpindlePoles;
	private double[] voxelSizesForAnalysis;
	private RandomAccessibleInterval< BitType > initialDnaMask;
	private EllipsoidVectors dnaEllipsoidVectors;
	private ProfileAndRadius dnaLateralProfileAndRadius;
	private double[] dnaAlignedSpindlePoleToPoleVector;
	private double[] dnaAlignedSpindleCenter;

	private Spindle3DMeasurements measurements;
	private RandomAccessibleInterval< R > raiXYCZ;
	private ArrayList< RandomAccessibleInterval< R > > rescaledVolumes;
	private AffineTransform3D dnaAlignedToSpindleAlignedTransform;
	private ArrayList< double[] > spindleAlignedSpindlePoles;

	public Spindle3DMorphometry( Spindle3DSettings settings, OpService opService, ScriptService scriptService )
	{
		this.settings = settings;
		this.opService = opService;
		this.scriptService = scriptService;
	}

	public String run( RandomAccessibleInterval<R> raiXYCZ )
	{
		Logger.log( "Running Spindle3D " + Spindle3DVersion.VERSION );

		this.raiXYCZ = raiXYCZ;

		objectMeasurements = new HashMap<>();

		measurements = new Spindle3DMeasurements( objectMeasurements );

		try
		{
			measurements.log += measure();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			measurements.log += "Exception during computation: \n" + e.toString();
		}

		measurements.setObjectMeasurements();
		return measurements.log;
	}

	public Spindle3DMeasurements getMeasurements()
	{
		return measurements;
	}

	private String measure()
	{
		measurements.version = settings.version;

		/**
		 * TODO:
		 * - maybe smooth the dna and tubulin image to reduce noise?
		 * - for low dynamic ranges, the smoothing should be done in a doubletype image
		 */

		createIsotropicallyResampledImages();

		if ( cellMask == null )
			cellMask = tryCreateCellMask( settings );

		measurements.dnaInitialThreshold = measureInitialThreshold( "DNA", dna, settings.initialThresholdFactor, cellMask );

		if ( measurements.dnaInitialThreshold < settings.minimalDynamicRange )
			return Spindle3DMeasurements.ANALYSIS_INTERRUPTED_LOW_DYNAMIC_DNA;

		initialDnaMask = createInitialDnaMask( dna, measurements.dnaInitialThreshold );

		dnaEllipsoidVectors = determineDnaAxes( initialDnaMask );

		rescaledToDnaAlignmentTransform = computeDnaAlignmentTransformAndAlignImages( dnaEllipsoidVectors );

		// DNA axial extend = width
		measureDnaWidth( dnaAlignedDna );

		// DNA lateral extend = length
		dnaLateralProfileAndRadius = measureDnaLength( dnaAlignedDna );

		// fka: Dna Hole
		measureDnaDilation( dnaLateralProfileAndRadius );

		measurements.dnaVolumeThreshold = measureDnaThreshold();

		Logger.log( "DNA Otsu threshold: " + measurements.dnaVolumeThreshold );

		dnaAlignedDnaMask = createDnaMaskAndMeasureDnaVolume( dnaAlignedDna, measurements.dnaVolumeThreshold );

		measurements.spindleThreshold = measureSpindleThreshold( dnaAlignedTubulin, dnaAlignedDnaMask );

		if ( measurements.spindleThreshold < settings.minimalDynamicRange )
			return Spindle3DMeasurements.ANALYSIS_INTERRUPTED_LOW_DYNAMIC_TUBULIN;

		dnaAlignedSpindleMask = createSpindleMask( dnaAlignedTubulin, measurements.spindleThreshold );

		// TODO PAPER: This was used for computations, should we keep it?
		if ( settings.smoothSpindle )
			Spindle3DAlgorithms.openFast( dnaAlignedSpindleMask );

		if ( settings.showIntermediateImages )
			show( dnaAlignedSpindleMask,
					"spindle volume mask", null,
					voxelSizesForAnalysis, false );

		dnaAlignedSpindlePoles = measureSpindlePoleLocations( dnaAlignedSpindleMask, dnaAlignedTubulin );

		dnaAlignedSpindlePoleToPoleVector = getVector( dnaAlignedSpindlePoles );

		dnaAlignedSpindleCenter = getMiddle( dnaAlignedSpindlePoles );

		measureDnaCenterToSpindleCenterDistance( dnaAlignedSpindleCenter );

		dnaAlignedToSpindleAlignedTransform = createSpindlePolesTransformAndAlignImages( dnaAlignedSpindlePoles, dnaAlignedSpindleCenter );

		measurements.spindleVolume = measureVolume( spindleAlignedSpindleMask );

		if ( spindleAlignedCellMask != null )
			measurements.cellVolume = measureVolume( spindleAlignedCellMask );

		measurements.spindleIntensityVariation
				= Utils.measureCoefficientOfVariation(
						spindleAlignedTublin,
						spindleAlignedSpindleMask,
						measurements.spindleThreshold
				);

		measurements.spindleSumIntensityRaw = Utils.measureSum(
				spindleAlignedTublin,
				spindleAlignedSpindleMask,
				0.0 );

		if ( spindleAlignedCellMask != null)
			measurements.cellTubulinSumIntensityRaw = Utils.measureSum(
				spindleAlignedTublin,
				spindleAlignedCellMask,
				0.0 );

		measurements.spindleSumIntensityCorrected = Utils.measureSum(
				spindleAlignedTublin,
				spindleAlignedSpindleMask,
				measurements.spindleThreshold );

		measureSpindleWidth( spindleAlignedSpindleMask );

		measurements.spindleAspectRatio = measurements.spindleLength / measurements.spindleWidthAvg;

		measureSpindleAxisToCoverslipPlaneAngle( dnaAlignedSpindlePoles );

		return Spindle3DMeasurements.ANALYSIS_FINISHED;
	}

	private RandomAccessibleInterval< BitType > tryCreateCellMask( Spindle3DSettings< R > settings )
	{
		if ( settings.roiDetectionMacro != null && settings.roiDetectionMacro.exists() )
		{
			final ArrayList< RandomAccessibleInterval< R > > list = new ArrayList<>();
			list.add( tubulin );
			list.add( dna );
			final RandomAccessibleInterval< R > stack = Views.stack( list );
			final ImagePlus wrap = ImageJFunctions.wrap( Views.permute( stack, 2, 3 ), "" );

			final ScriptRunner scriptRunner = new ScriptRunner( wrap, settings.roiDetectionMacro, scriptService );
			scriptRunner.run();
			RandomAccessibleInterval< UnsignedByteType > mask = ImageJFunctions.wrapByte( scriptRunner.getOutputImp() );
			return Converters.convert( mask, ( i, o ) ->
					o.set( i.getRealDouble() > 0.5 ? true : false ), new BitType() );
		}
		else
		{
			return null;
		}
	}

	private double measureDnaThreshold()
	{
		/**
		 * Crop a box around the DNA, rather taking more voxels along the
		 * dna (spindle) axis, because there should'nt be any DNA signals
		 * from other cells.
		 */

		final FinalInterval boxAroundDna = FinalInterval.createMinMax(
				-( int ) ( measurements.metaphasePlateLength / 2.0 / settings.voxelSizeForAnalysis ),
				-( int ) ( measurements.metaphasePlateLength / 2.0 / settings.voxelSizeForAnalysis ),
				-( int ) ( measurements.metaphasePlateWidth / settings.voxelSizeForAnalysis ),
				+( int ) ( measurements.metaphasePlateLength / 2.0 / settings.voxelSizeForAnalysis ),
				+( int ) ( measurements.metaphasePlateLength / 2.0 / settings.voxelSizeForAnalysis ),
				+( int ) ( measurements.metaphasePlateWidth / settings.voxelSizeForAnalysis ) );

		final IntervalView croppedDna = Views.interval( Views.extendBorder( dnaAlignedDna ), boxAroundDna );

		//ImageJFunctions.show( Views.permute( Views.addDimension( dnaAlignedDna, 0, 0), 2,3), "DNA" );

		//ImageJFunctions.show( Views.permute( Views.addDimension( croppedDna, 0, 0), 2,3), "cropped DNA" );

		return Algorithms.thresholdOtsu( croppedDna );
	}

	private double[] getMiddle( ArrayList< double[] > vectors )
	{
		double[] middle = new double[ 3 ];
		LinAlgHelpers.add( vectors.get( 0 ), vectors.get( 1 ), middle );
		for ( int d = 0; d < 3; d++ )
		{
			middle[ d ] = 0.5 * middle[ d ];
		}
		return middle;
	}

	private int getVoxelBoundingBoxMin( RealPoint cellCentreMicrometer, int d )
	{
		return ( int ) ( cellCentreMicrometer.getDoublePosition( d ) / settings.voxelSizeForAnalysis - 1.5 * settings.cellRadius / settings.voxelSizeForAnalysis );
	}

	private int getVoxelBoundingBoxMax( RealPoint cellCentreMicrometer, int d )
	{
		return ( int ) ( cellCentreMicrometer.getDoublePosition( d ) / settings.voxelSizeForAnalysis + 1.5 * settings.cellRadius / settings.voxelSizeForAnalysis );
	}

	private void measureSpindleWidth( RandomAccessibleInterval< BitType > alignedSpindleMask )
	{
		Logger.log( "Creating projection of spindle mask along spindle axis..." );

		RandomAccessibleInterval< BitType > projectedMask =
				new Projection<>(
					alignedSpindleMask,
					2 ).maximum();

		// remove spurious microtubules sticking out
		projectedMask = Algorithms.open( projectedMask, 2 );

		if ( settings.showIntermediateImages )
			show( projectedMask,
					"Spindle mask projection along pole to pole axis",
					null,
					voxelSizesForAnalysis,
					false);

		final ArrayList< Long > widths = measureRadialWidthsInPixels( projectedMask );

		Collections.sort( widths );

		measurements.spindleWidthAvg = widths.stream().mapToDouble( x -> x ).summaryStatistics().getAverage() * settings.voxelSizeForAnalysis ;
		measurements.spindleWidthMin = widths.get( 0 ) * settings.voxelSizeForAnalysis;
		measurements.spindleWidthMax = widths.get( widths.size() - 1 ) * voxelSizesForAnalysis[ 1 ];
	}

	public Double measureVolume( RandomAccessibleInterval< BitType > mask )
	{
		return Measurements.measureSizeInPixels( mask ) * Math.pow( settings.voxelSizeForAnalysis, 3 );
	}

	@Deprecated
	public double measureSpindleThresholdOLD(
			RandomAccessibleInterval< R > dnaAlignedTubulin,
			RandomAccessibleInterval< BitType > dnaAlignedDnaMask )
	{
		RandomAccessibleInterval< BitType > dilateDnaMask = Algorithms.dilate( dnaAlignedDnaMask, 1 );

		final double dnaLateralHalfWidth = measurements.metaphasePlateLength / 2.0 ;
		final double dnaAxialHalfWidth = measurements.metaphasePlateWidth / 2.0 ;
		final long lateralHalfWidth = (long) ( ( dnaLateralHalfWidth + 1.0 ) / settings.voxelSizeForAnalysis );
		final long axialHalfWidth = (long) ( ( dnaAxialHalfWidth + 1.0 ) / settings.voxelSizeForAnalysis );

		final FinalInterval interval = FinalInterval.createMinMax(
				-lateralHalfWidth,
				-lateralHalfWidth,
				-axialHalfWidth,
				lateralHalfWidth,
				lateralHalfWidth,
				axialHalfWidth );

		IntervalView< R > spindleThresholdMeasurementRegion = Views.interval( dnaAlignedTubulin, interval );

		if ( settings.showIntermediateImages )
			show( spindleThresholdMeasurementRegion, "tubulin threshold measurement region", null, voxelSizesForAnalysis, false );


		final double minInsideSpindleDistSquared = Math.pow( dnaLateralHalfWidth - 2.0, 2);
		final double minInsideCellDistSquared = Math.pow( dnaLateralHalfWidth, 2);

		final ArrayList< Double > cytoplasmicTubulinValues = new ArrayList<>();
		final ArrayList< Double > spindleTubulinValues = new ArrayList<>();

		final RandomAccess< BitType > dnaAlignedDnaMaskAccess = dnaAlignedDnaMask.randomAccess();

		final Cursor< R > cursor = Views.iterable( spindleThresholdMeasurementRegion ).cursor();
		int[] position = new int[ 3 ];
		double lateralDistSquared;

		while( cursor.hasNext() )
		{
			cursor.next();

			dnaAlignedDnaMaskAccess.setPosition( cursor );

			if ( dnaAlignedDnaMaskAccess.get().get() )
			{
				// pixels containing DNA exclude tubulin and thus would
				// lead to a too low threshold
				continue;
			}

			cursor.localize( position );

			// we only compute the lateral distance
			// that is, the distance to the spindle axis
			lateralDistSquared = 0;
			for ( int d = 0; d < 2; d++ )
				lateralDistSquared += Math.pow( position[ d ] * settings.voxelSizeForAnalysis, 2 );

			if ( lateralDistSquared < minInsideSpindleDistSquared )
			{
				spindleTubulinValues.add( cursor.get().getRealDouble() );
				continue;
			}

			if ( lateralDistSquared > minInsideCellDistSquared ) continue;

			cytoplasmicTubulinValues.add( cursor.get().getRealDouble() );
		}

		final double medianCytoplasm = Utils.median( cytoplasmicTubulinValues );
		final double madCytoplasm = Utils.mad( cytoplasmicTubulinValues, medianCytoplasm );
		final double meanCytoplasm = Utils.mean( cytoplasmicTubulinValues );
		final double sdevCytoplasm = Utils.sdev( cytoplasmicTubulinValues, meanCytoplasm );
		final double meanSpindle = Utils.mean( spindleTubulinValues );
		final double sdevSpindle = Utils.sdev( spindleTubulinValues, meanSpindle );

		// Note: sdev = 1.25 * mad => to get similar thresholds we have to go a bit higher with the mad
		Logger.log( "Tubulin intensity cytoplasm (median +/- mad): " + medianCytoplasm + " +/- " + madCytoplasm );
		Logger.log( "Tubulin intensity cytoplasm (mean +/- sdev): " + (int) meanCytoplasm + " +/- " + (int) sdevCytoplasm + "; numPixels: " + cytoplasmicTubulinValues.size());
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 2 * mad): " + (int) ( medianCytoplasm + 2 * madCytoplasm ) );
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 3 * mad): " + (int) ( medianCytoplasm + 3 * madCytoplasm ) );
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 4 * mad): " + (int) ( medianCytoplasm + 4 * madCytoplasm ) );
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 5 * mad): " + (int) ( medianCytoplasm + 5 * madCytoplasm ) );
		Logger.log( "Tubulin intensity spindle (mean +/- sdev): " + (int) meanSpindle + " +/- " + (int) sdevSpindle + "; numPixels: " + spindleTubulinValues.size() );

//		final double threshold = settings.spindleThresholdFactor * ( meanSpindle - meanCytoplasm ) + meanCytoplasm;

		final double threshold = medianCytoplasm + 5 * madCytoplasm;
		Logger.log( "Spindle threshold = median + 5 * mad: " + threshold );

		measurements.spindleSNR = ( meanSpindle - meanCytoplasm ) / Math.sqrt( sdevCytoplasm * sdevCytoplasm + sdevSpindle * sdevSpindle );

		Logger.log( "Spindle SNR = ( <spindle> - <cyto> ) / sqrt( var(spindle) + var(cyto) ): " + measurements.spindleSNR );

		if ( Double.isNaN( threshold ) )
		{
			measurements.log += "Error: Spindle threshold was NaN";
			throw new RuntimeException( "Spindle threshold was NaN" );
		}

		return threshold;
	}


	/**
	 *
	 * @param dnaAlignedTubulin
	 * @param dnaAlignedDnaMask
	 * @return
	 */
	public double measureSpindleThreshold(
			RandomAccessibleInterval< R > dnaAlignedTubulin,
			RandomAccessibleInterval< BitType > dnaAlignedDnaMask )
	{
		// Create an interval that full encloses the DNA
		final double dnaLateralHalfWidth = measurements.metaphasePlateLength / 2.0 ;
		final double dnaAxialHalfWidth = measurements.metaphasePlateWidth / 2.0 ;
		final long lateralHalfWidth = (long) ( ( dnaLateralHalfWidth + 2.0 ) / settings.voxelSizeForAnalysis );
		final long axialHalfWidth = (long) ( ( dnaAxialHalfWidth + 2.0 ) / settings.voxelSizeForAnalysis );

		final FinalInterval dnaEnclosingInterval = FinalInterval.createMinMax(
				-lateralHalfWidth,
				-lateralHalfWidth,
				-axialHalfWidth,
				lateralHalfWidth,
				lateralHalfWidth,
				axialHalfWidth );

		IntervalView< R > tubulinCrop = Views.interval( dnaAlignedTubulin, dnaEnclosingInterval );

		if ( settings.showIntermediateImages )
			show( tubulinCrop, "tubulin threshold measurement region", null, voxelSizesForAnalysis, false );

		final double maxInsideSpindleDistSquared = Math.pow( dnaLateralHalfWidth - 2.0, 2);

		final ArrayList< Double > cytoplasmicTubulinValues = new ArrayList<>();
		final ArrayList< Double > spindleTubulinValues = new ArrayList<>();
		final ArrayList< Double > dnaPeripheryTubulinValues = new ArrayList<>();

		// dilate Dna to create a region within there are bona-fide tubulin intensities
		RandomAccessibleInterval< BitType > dilateDnaMask = Algorithms.dilate( dnaAlignedDnaMask, 1 );

		final RandomAccess< BitType > dnaMaskAccess = dnaAlignedDnaMask.randomAccess();
		final RandomAccess< BitType > dilateDnaMaskAccess = dilateDnaMask.randomAccess();

		final Cursor< R > cursor = Views.iterable( tubulinCrop ).cursor();
		int[] position = new int[ 3 ];
		double lateralDistSquared;

		while( cursor.hasNext() )
		{
			cursor.next();

			if ( dnaMaskAccess.setPositionAndGet( cursor ).get() )
			{
				// pixels containing DNA exclude tubulin and thus would
				// lead to a too low threshold => exclude
				continue;
			}

			if ( ! dilateDnaMaskAccess.setPositionAndGet( cursor ).get() )
			{
				// pixels that are outside the dilate dna mask
				// could be outside the cell => exclude
				continue;
			}

			cursor.localize( position );

			dnaPeripheryTubulinValues.add( cursor.get().getRealDouble()  );

			// we only compute the lateral distance
			// that is, the distance to the spindle axis
			lateralDistSquared = 0;
			for ( int d = 0; d < 2; d++ )
				lateralDistSquared += Math.pow( position[ d ] * settings.voxelSizeForAnalysis, 2 );

			if ( lateralDistSquared < maxInsideSpindleDistSquared )
			{
				// pixel is close to spindle axis
				// => add to the spindle values
				spindleTubulinValues.add( cursor.get().getRealDouble() );
			}
			else
			{
				cytoplasmicTubulinValues.add( cursor.get().getRealDouble() );
			}
		}

		// THIS IS WHAT IS ACTUALLY USED
		double thresholdOtsu = Spindle3DUtils.thresholdOtsu( dnaPeripheryTubulinValues );

		final double medianCytoplasm = Utils.median( cytoplasmicTubulinValues );
		final double madCytoplasm = Utils.mad( cytoplasmicTubulinValues, medianCytoplasm );
		final double meanCytoplasm = Utils.mean( cytoplasmicTubulinValues );
		final double sdevCytoplasm = Utils.sdev( cytoplasmicTubulinValues, meanCytoplasm );
		final double meanSpindle = Utils.mean( spindleTubulinValues );
		final double sdevSpindle = Utils.sdev( spindleTubulinValues, meanSpindle );

		// Note: sdev = 1.25 * mad => to get similar thresholds we have to go a bit higher with the mad
		Logger.log( "Tubulin intensity cytoplasm (median +/- mad): " + medianCytoplasm + " +/- " + madCytoplasm );
		Logger.log( "Tubulin intensity cytoplasm (mean +/- sdev): " + (int) meanCytoplasm + " +/- " + (int) sdevCytoplasm + "; numPixels: " + cytoplasmicTubulinValues.size());
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 2 * mad): " + (int) ( medianCytoplasm + 2 * madCytoplasm ) );
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 3 * mad): " + (int) ( medianCytoplasm + 3 * madCytoplasm ) );
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 4 * mad): " + (int) ( medianCytoplasm + 4 * madCytoplasm ) );
		Logger.log( "Tubulin intensity cytoplasm based threshold (median + 5 * mad): " + (int) ( medianCytoplasm + 5 * madCytoplasm ) );
		Logger.log( "Tubulin intensity spindle (mean +/- sdev): " + (int) meanSpindle + " +/- " + (int) sdevSpindle + "; numPixels: " + spindleTubulinValues.size() );
		Logger.log( "Tubulin Otsu threshold: " + thresholdOtsu );

		final double threshold = thresholdOtsu; // medianCytoplasm + 5 * madCytoplasm;
		// Logger.log( "Spindle threshold = median + 5 * mad: " + threshold );
		Logger.log( "Spindle threshold = Tubulin Otsu threshold: " + threshold );

		measurements.spindleSNR = ( meanSpindle - meanCytoplasm ) / Math.sqrt( sdevCytoplasm * sdevCytoplasm + sdevSpindle * sdevSpindle );

		Logger.log( "Spindle SNR = ( <spindle> - <cyto> ) / sqrt( var(spindle) + var(cyto) ): " + measurements.spindleSNR );

		if ( Double.isNaN( threshold ) )
		{
			measurements.log += "Error: Spindle threshold was NaN";
			throw new RuntimeException( "Spindle threshold was NaN" );
		}

		return threshold;
	}

	private RandomAccessibleInterval< R > createMaximumProjectionAlongSpindleAxis(
			RandomAccessibleInterval< R > poleToPoleAlignedSpindleRai )
	{
		Logger.log( "Computing maximum projection of spindle along spindle axis..." );

		final FinalInterval spindleInterval = createSpindleInterval();

		Projection projection = new Projection<>(
				Views.interval( poleToPoleAlignedSpindleRai, spindleInterval ),
				2 );

		final RandomAccessibleInterval< R > maximum = projection.maximum();

		if ( settings.showIntermediateImages )
			show( maximum, "Spindle maximum projection along pole to pole axis",
					null, voxelSizesForAnalysis, false);

		return maximum;
	}

	private FinalInterval createSpindleInterval()
	{
		long[] min = new long[3];
		long[] max = new long[3];

		max[ 0 ] = (long) Math.ceil( measurements.metaphasePlateLength
				/ 2.0 / settings.voxelSizeForAnalysis );
		max[ 1 ] = (long) Math.ceil( measurements.metaphasePlateLength
				/ 2.0 / settings.voxelSizeForAnalysis );
		max[ 2 ] = (long) Math.ceil( 1.2 * measurements.spindleLength
				/ 2.0 / settings.voxelSizeForAnalysis );

		for ( int d = 0; d < 3; d++ )
			min[ d ] = - max[ d ];

		return new FinalInterval( min, max );
	}


	/**
	 * Align spindle along pole to pole axis:
	 * After the transformation (0,0,0) is the spindle center
	 * and the z-axis is the pole-to-pole axis.
	 *
	 * @param spindlePoles
	 * @param spindleCenter
	 * @return
	 */
	private AffineTransform3D createSpindlePolesTransformAndAlignImages( ArrayList< double[] > spindlePoles, double[] spindleCenter )
	{
		dnaAlignedToSpindleAlignedTransform = computeTransform( spindlePoles, spindleCenter );

		spindleAlignedTublin = Transforms.createTransformedView( dnaAlignedTubulin, dnaAlignedToSpindleAlignedTransform );
		spindleAlignedDna = Transforms.createTransformedView( dnaAlignedDna, dnaAlignedToSpindleAlignedTransform );
		spindleAlignedDnaMask = Transforms.createTransformedView( dnaAlignedDnaMask, dnaAlignedToSpindleAlignedTransform, new NearestNeighborInterpolatorFactory() );
		spindleAlignedSpindleMask = Transforms.createTransformedView( dnaAlignedSpindleMask, dnaAlignedToSpindleAlignedTransform, new NearestNeighborInterpolatorFactory() );
		if ( dnaAlignedCellMask != null )
			spindleAlignedCellMask = Transforms.createTransformedView( dnaAlignedCellMask, dnaAlignedToSpindleAlignedTransform, new NearestNeighborInterpolatorFactory() );

		spindleAlignedSpindlePoles = new ArrayList<>();
		for ( int i = 0; i < 2; i++ )
		{
			final double[] transformed = transformed( spindlePoles.get( i ), dnaAlignedToSpindleAlignedTransform );
			spindleAlignedSpindlePoles.add( transformed );
		}

		if ( settings.showIntermediateImages )
		{
			final ArrayList< RealPoint > interestPoints = new ArrayList<>();

			for ( int i = 0; i < 2; i++ )
			{
				interestPoints.add( new RealPoint( spindleAlignedSpindlePoles.get( i ) ) );
			}
			interestPoints.add( new RealPoint( 0,0,0 ) );

			show( spindleAlignedTublin,
					"spindle aligned pole to pole",
					interestPoints,
					voxelSizesForAnalysis,
					false );
		}

		return dnaAlignedToSpindleAlignedTransform;
	}

	private double[] transformed( double[] location, AffineTransform3D affineTransform3D )
	{
		final double[] transformed = new double[ 3 ];
		// transformation is in voxel units
		final double[] voxels = toVoxels( location );
		affineTransform3D.apply( voxels, transformed );
		final double[] calibrated = toCalibrated( transformed );
		return calibrated;
	}

	private AffineTransform3D computeTransform( ArrayList< double[] > spindlePoles, double[] spindleCenter )
	{
		final double[] poleToPoleAxis = new double[ 3 ];
		LinAlgHelpers.subtract( spindlePoles.get( 0 ), spindlePoles.get( 1 ), poleToPoleAxis );
		LinAlgHelpers.normalize( poleToPoleAxis );

		// put spindle center in the center
		final double[] spindleCenterVoxelUnits = toVoxels( spindleCenter );
		AffineTransform3D dnaAlignedToSpindleAlignedTransform = new AffineTransform3D();
		dnaAlignedToSpindleAlignedTransform.translate( spindleCenterVoxelUnits );
		dnaAlignedToSpindleAlignedTransform = dnaAlignedToSpindleAlignedTransform.inverse();

		// rotate spindle along z-axis
		AffineTransform3D poleToPoleAxisRotation =
				Transforms.getRotationTransform3D( new double[]{ 0, 0, 1 }, poleToPoleAxis );
		dnaAlignedToSpindleAlignedTransform.preConcatenate( poleToPoleAxisRotation );

		return dnaAlignedToSpindleAlignedTransform;
	}

	private double[] toVoxels( double[] location )
	{
		final double[] spindleCenterVoxelUnits = location.clone();
		Utils.divide( spindleCenterVoxelUnits, settings.voxelSizeForAnalysis );
		return spindleCenterVoxelUnits;
	}

	private double[] toCalibrated( double[] location )
	{
		final double[] spindleCenterVoxelUnits = location.clone();
		Utils.divide( spindleCenterVoxelUnits, 1.0 / settings.voxelSizeForAnalysis );
		return spindleCenterVoxelUnits;
	}


	public ArrayList< Long > measureRadialWidthsInPixels( RandomAccessibleInterval< BitType > mask )
	{
		RealRandomAccessible< BitType > rra =
				Views.interpolate(
						Views.extendZero( mask ), new NearestNeighborInterpolatorFactory<>() );

		double dAngle = Math.PI / 18; // = 10 degrees

		final ArrayList< Long > widthAtAngles = new ArrayList<>();

		for ( double angle = 0; angle < Math.PI; angle += dAngle )
		{
			final AffineTransform2D transform2D = new AffineTransform2D();
			transform2D.rotate( angle );

			IntervalView< BitType > rotated =
					Views.interval(
							Views.raster(
									RealViews.transform( rra, transform2D ) ), mask );

			widthAtAngles.add( Utils.countNonZeroPixelsAlongAxis( rotated, 0 ) );
		}
		return widthAtAngles;
	}

	private void measureSpindleAxisToCoverslipPlaneAngle( ArrayList< double[] > dnaAlignedSpindlePoles )
	{
		final ArrayList< double[] > coverslipAlignedSpindlePoles = new ArrayList<>();
		for ( double[] pole : dnaAlignedSpindlePoles )
		{
			coverslipAlignedSpindlePoles.add( transformToCoverslipCoordinateSystem( rescaledToDnaAlignmentTransform, pole ) );
		}

		final double[] vector = getVector( coverslipAlignedSpindlePoles );
		measurements.spindleAngle =
				90.0 - Math.abs( 180.0 / Math.PI *
						Transforms.getAngle( new double[]{ 0, 0, 1 }, vector ) );
	}

	public void measureDnaCenterToSpindleCenterDistance( double[] spindleCenter )
	{
		final double[] dnaCentre = { 0, 0, 0 };
		measurements.spindleCenterToMetaphasePlateCenterDistance = LinAlgHelpers.distance( dnaCentre, spindleCenter);
	}

	public double[] getVector( ArrayList< double[] > positions )
	{
		final double[] vector = new double[ 3 ];

		LinAlgHelpers.subtract( positions.get( 0 ), positions.get( 1 ), vector );

		return vector;
	}

	public void createIsotropicallyResampledImages()
	{
		Logger.log( "Create isotropic images..." );

		voxelSizesForAnalysis = Utils.as3dDoubleArray( settings.voxelSizeForAnalysis );

		long numChannels = raiXYCZ.dimension( 2 );

		rescaledVolumes = new ArrayList<>();

		final double[] scalingFactors = getScalingFactors( settings.inputCalibration, settings.voxelSizeForAnalysis );

		for ( int c = 0; c < numChannels; c++ )
		{
			final IntervalView< R > volume = Views.hyperSlice( raiXYCZ, 2, c );
			final RandomAccessibleInterval< R > rescaledVolume =
					createRescaledArrayImg(
							volume,
							scalingFactors );
			rescaledVolumes.add( rescaledVolume );
		}

		dna = rescaledVolumes.get( ( int ) settings.dnaChannelIndex );
		tubulin = rescaledVolumes.get( ( int ) settings.tubulinChannelIndex );

		if ( cellMask != null )
		{
			cellMask = createRescaledArrayImg( cellMask, scalingFactors );
		}

		if ( settings.showIntermediateImages )
		{
			show( dna, "DNA isotropic voxel size", null, voxelSizesForAnalysis, false );
			show( tubulin, "spindle isotropic voxel size", null, voxelSizesForAnalysis, false );
			if ( cellMask != null )
				show( cellMask, "cell mask isotropic voxel size", null, voxelSizesForAnalysis, false );

		}
	}

	public double measureInitialThreshold( final String channel, RandomAccessibleInterval< R > rai, double thresholdFactor, RandomAccessibleInterval< BitType > mask )
	{
		final double[] scalingFactors = getScalingFactors( new double[]{
						settings.voxelSizeForAnalysis,
						settings.voxelSizeForAnalysis,
						settings.voxelSizeForAnalysis },
						settings.voxelSizeForInitialDNAThreshold );

		final RandomAccessibleInterval< R > downscaled = createRescaledArrayImg( rai, scalingFactors );

		if ( mask != null )
		{
			mask = Scalings.createResampledArrayImg( mask, scalingFactors );
			//Viewers.showRai3dWithImageJ( mask, "DNA Threshold Mask" );
			//Viewers.showRai3dWithImageJ( downscaled, "DNA Threshold" );
		}

		Pair< Double, Double > minMaxValues;

		if ( mask != null )
		{
			minMaxValues = Spindle3DAlgorithms.getMinMaxValues( downscaled, mask );
		}
		else
		{
			minMaxValues = Algorithms.getMinMaxValues( downscaled );
		}

		Logger.log( channel + " downscaled minimum value: " + minMaxValues.getA()  );
		Logger.log( channel + " downscaled maximum value: " + minMaxValues.getB()  );
		Logger.log( channel + " initial threshold factor: " + thresholdFactor );
		double initialDnaThreshold = ( minMaxValues.getB() - minMaxValues.getA() ) * thresholdFactor + minMaxValues.getA() ;
		Logger.log( channel + " initial threshold = (max-min)*factor + min: " + initialDnaThreshold );

		return initialDnaThreshold;
	}

	private RandomAccessibleInterval< BitType > createInitialDnaMask(
			RandomAccessibleInterval< R > dna,
			double dnaThreshold )
	{
		RandomAccessibleInterval< BitType > dnaMask = createMask( dna, dnaThreshold, opService );
		final int numRemainingRegions = Spindle3DAlgorithms.removeRegionsTouchingImageBorders( dnaMask, 2 );
		if ( numRemainingRegions == 0 )
		{
			final String log = "All initial DNA regions were touching the image border!";
			measurements.log += log;
			throw new RuntimeException( log );
		}
		Regions.onlyKeepLargestRegion( dnaMask, ConnectedComponents.StructuringElement.EIGHT_CONNECTED );

		// final RandomAccessibleInterval< BitType > dnaMask = createCentralObjectsMask( dna, dnaThreshold );

		/**
		 * Morphological filtering
		 *
		 * it appears that the principal axes determination
		 * is not working robustly
		 * if the meta-phase plate is too thick
		 *
		 * This was mainly an issue using MLJ, now with 3D Image Suite, this issue seems solved
		 */

//		final RandomAccessibleInterval< BitType > processedDnaMask = createProcessedMetaPhasePlate( dnaMask );

//		if ( settings.showIntermediateResults )
//			show( processedDnaMask, "eroded DNA mask", null, workingCalibration, false );

		if ( settings.showIntermediateImages )
		{
			show( dnaMask, "initial dna mask", null, voxelSizesForAnalysis, false );
		}

		return dnaMask;
	}

	private AffineTransform3D computeDnaAlignmentTransformAndAlignImages( EllipsoidVectors ellipsoidVectors )
	{
		Logger.log( "Creating aligned images..." );

		rescaledToDnaAlignmentTransform =
				Ellipsoids3DImageSuite.createShortestAxisAlignmentTransform( ellipsoidVectors );

		dnaAlignedTubulin = Utils.copyAsArrayImg(
				Transforms.createTransformedView( tubulin, rescaledToDnaAlignmentTransform, new NearestNeighborInterpolatorFactory(), Transforms.BorderExtension.ExtendBorder ) );

		dnaAlignedDna = Utils.copyAsArrayImg(
				Transforms.createTransformedView( dna, rescaledToDnaAlignmentTransform, new NearestNeighborInterpolatorFactory(), Transforms.BorderExtension.ExtendBorder ) );

		dnaAlignedInitialDnaMask = Utils.copyAsArrayImg(
				Transforms.createTransformedView( initialDnaMask, rescaledToDnaAlignmentTransform, new NearestNeighborInterpolatorFactory() ) );

		if ( cellMask != null )
			dnaAlignedCellMask = Utils.copyAsArrayImg(
					Transforms.createTransformedView( cellMask, rescaledToDnaAlignmentTransform, new NearestNeighborInterpolatorFactory() ) );

		if ( settings.showIntermediateImages )
		{
			final String aligned = " aligned along DNA shortest axis";

			show( dnaAlignedTubulin, "tubulin" + aligned, Transforms.origin(),
					voxelSizesForAnalysis, false );

			show( dnaAlignedDna, "DNA" + aligned, Transforms.origin(),
					voxelSizesForAnalysis, false );

			show( dnaAlignedInitialDnaMask, "DNA initial mask" + aligned,
					Transforms.origin(), voxelSizesForAnalysis, false );
		}

		return rescaledToDnaAlignmentTransform;
	}

	private EllipsoidVectors determineDnaAxes( RandomAccessibleInterval< BitType > dnaMask )
	{
		Logger.log( "Determining DNA axes..." );

		return Ellipsoids3DImageSuite.fitEllipsoid( Utils.getAsImagePlusMovie( dnaMask, "" ) );
	}

	private void measureDnaWidth( RandomAccessibleInterval alignedDNA )
	{
		Logger.log( "Measuring DNA width..." );

		final CoordinatesAndValues dnaProfileAlongDnaAxis =
				Utils.computeAverageIntensitiesAlongAxis(
						alignedDNA,
						settings.maxMetaphasePlateLength / 2.0,
						2,
						- settings.maxMetaphasePlateWidth / 2.0,
						+ settings.maxMetaphasePlateWidth / 2.0,
						settings.voxelSizeForAnalysis );

		final CoordinatesAndValues dnaProfileAlongDnaAxisDerivative =
				CurveAnalysis.derivative(
						dnaProfileAlongDnaAxis,
						( int ) Math.ceil( settings.metaphasePlateWidthDerivativeDelta / settings.voxelSizeForAnalysis ) );

		final ArrayList< CoordinateAndValue > dnaAxialBoundaries =
				CurveAnalysis.leftMaxAndRightMinLoc( dnaProfileAlongDnaAxisDerivative );

		measurements.metaphasePlateWidth =
				dnaAxialBoundaries.get( 1 ).coordinate -
						dnaAxialBoundaries.get( 0 ).coordinate;

		if ( settings.showIntermediatePlots )
		{
			Plots.plot( dnaProfileAlongDnaAxis,
					"distance to center", "DNA intensity along DNA axis" );
			Plots.plot( dnaProfileAlongDnaAxisDerivative,
					"distance to center", "d/dx DNA intensity along DNA axis" );
		}

		//logDnaAxialThreshold( dnaProfileAlongDnaAxis, dnaAxialBoundaries );
	}

	private void logDnaAxialThreshold(
			CoordinatesAndValues dnaProfileAlongDnaAxis,
			ArrayList< CoordinateAndValue > dnaAxialBoundaries )
	{
		for ( int i = 0; i < 2; i++ )
		{
			final Double valueAtCoordinate =
					CurveAnalysis.getValueAtCoordinate( dnaProfileAlongDnaAxis,
							dnaAxialBoundaries.get( i ).coordinate );
			Logger.log( "DNA axial profile threshold " + i + ": " + valueAtCoordinate );
		}
	}

	private ProfileAndRadius measureDnaLength( RandomAccessibleInterval< R > alignedDNA )
	{
		Logger.log( "Measuring DNA length..." );

		Projection projection = new Projection(
				alignedDNA,
				2,
				( long ) ( - settings.maxMetaphasePlateWidth / 2.0 / settings.voxelSizeForAnalysis ),
				( long ) ( + settings.maxMetaphasePlateWidth / 2.0 / settings.voxelSizeForAnalysis ));
		final RandomAccessibleInterval< R > dnaProjectionAlongDnaAxis = projection.maximum();

		final ProfileAndRadius dnaLateralProfileAndRadius =
				measureRadialProfileAndRadius(
						dnaProjectionAlongDnaAxis,
						"dna length",
						settings.metaphasePlateLengthDerivativeDelta,
						settings.maxMetaphasePlateLength / 2.0 );

		measurements.metaphasePlateLength = 2.0 * dnaLateralProfileAndRadius.radius;

		return dnaLateralProfileAndRadius;
	}

	/**
	 * Find the spindle poles
	 *
	 * @param dnaAlignedSpindleMask
	 * @param dnaAlignedTubulin
	 * @return
	 */
	private ArrayList< double[] > measureSpindlePoleLocations(
			final RandomAccessibleInterval< BitType > dnaAlignedSpindleMask,
			final RandomAccessibleInterval< R > dnaAlignedTubulin )
	{
		final ArrayList< double[] > spindlePoles = determineSpindlePolesAlongDnaAxisFromSpindleMask( dnaAlignedSpindleMask );

//		final double distance = LinAlgHelpers.distance( spindlePoles.get( 0 ), spindlePoles.get( 1 ) );
//		System.out.println("[DEBUG] Spindle_length: " + distance);

		final ArrayList< double[] > refinedSpindlePoles = refineSpindlePoles( dnaAlignedTubulin, dnaAlignedSpindleMask, spindlePoles, settings.axialPoleRefinementRadius, settings.lateralPoleRefinementRadius );

//		final double distanceRefined = LinAlgHelpers.distance( refinedSpindlePoles.get( 0 ), refinedSpindlePoles.get( 1 ) );
//		System.out.println("[DEBUG] Refined Spindle_length: " + distanceRefined);

		measurements.spindlePoleARefinementDistance =
				LinAlgHelpers.distance(
						spindlePoles.get( 0 ), refinedSpindlePoles.get( 0 ) );

		measurements.spindlePoleBRefinementDistance =
				LinAlgHelpers.distance(
						spindlePoles.get( 1 ), refinedSpindlePoles.get( 1 ) );

		measurements.spindleLength = LinAlgHelpers.distance( refinedSpindlePoles.get( 0 ), refinedSpindlePoles.get( 1 ) );

		if ( settings.showIntermediateImages )
		{
			final ArrayList< RealPoint > interestPoints = new ArrayList<>();

			for ( int i = 0; i < 2; i++ )
			{
				interestPoints.add( new RealPoint( spindlePoles.get( i ) ) );
			}

			show( dnaAlignedTubulin,
					"dna aligned tubulin with spindle poles",
					interestPoints,
					voxelSizesForAnalysis,
					false  );
		}

		return refinedSpindlePoles;
	}

	private RandomAccessibleInterval< BitType > createDnaMaskAndMeasureDnaVolume( RandomAccessibleInterval< R > dna, Double dnaVolumeThreshold )
	{
		//final RandomAccessibleInterval< BitType > dnaFinalMask = createCentralObjectsMask( dna, dnaVolumeThreshold );

		RandomAccessibleInterval< BitType > dnaFinalMask = createMask( dna, dnaVolumeThreshold, opService );

		if ( settings.showIntermediateImages )
			show( Utils.copyAsArrayImg( dnaFinalMask ), "DNA final mask: all regions above threshold",
					null, voxelSizesForAnalysis, false );

		final int numRemainingRegions = Spindle3DAlgorithms.removeRegionsTouchingImageBorders( dnaFinalMask, 3 );
		if ( numRemainingRegions == 0 )
		{
			final String log = "All DNA regions were touching the image border!";
			measurements.log += log;
			throw new RuntimeException( log );
		}

		Regions.onlyKeepLargestRegion( dnaFinalMask, ConnectedComponents.StructuringElement.EIGHT_CONNECTED );

		final long dnaVolumeInPixels =
				Measurements.measureSizeInPixels( dnaFinalMask );

		measurements.chromatinVolume = dnaVolumeInPixels * Math.pow( settings.voxelSizeForAnalysis, 3 );

		if ( settings.showIntermediateImages )
		{
			show( Utils.copyAsArrayImg( dnaFinalMask ), "DNA final mask: without border regions",
					null, voxelSizesForAnalysis, false );

			show( Utils.copyAsArrayImg( dnaFinalMask ), "DNA final mask: only largest region",
					null, voxelSizesForAnalysis, false );
		}

		return dnaFinalMask;
	}

	private ArrayList< double[] > determineSpindlePolesAlongDnaAxisFromSpindleMask( RandomAccessibleInterval< BitType > dnaAlignedSpindleMask )
	{
		final CoordinatesAndValues binarySpindleProfile =
				Utils.computeMaximumIntensitiesAlongAxis(
						dnaAlignedSpindleMask,
						measurements.metaphasePlateLength / 2.0,
						Spindle3DMeasurements.ALIGNED_DNA_AXIS,
						settings.voxelSizeForAnalysis );

		// Since this is a binary plot, taking the derivative to
		// find the edges seems a bit of an overkill, but then why not.
		final int derivativeDeltaVoxels = 2;
		final CoordinatesAndValues tubulinProfileDerivative =
				CurveAnalysis.derivative(
						binarySpindleProfile,
						derivativeDeltaVoxels );

		ArrayList< CoordinateAndValue > tubulinExtrema =
				CurveAnalysis.leftMaxAndRightMinLoc( tubulinProfileDerivative );

		final ArrayList< double[] > spindlePoles = new ArrayList<>();
		spindlePoles.add( new double[]{ 0, 0, tubulinExtrema.get( 0 ).coordinate });
		spindlePoles.add( new double[]{ 0, 0, tubulinExtrema.get( 1 ).coordinate });

		if ( settings.showIntermediatePlots )
		{
			Plots.plot(
					binarySpindleProfile.coordinates,
					binarySpindleProfile.values,
					"center distance [um]",
					"spindle mask maxima along shortest DNA axis" );
			Plots.plot(
					tubulinProfileDerivative.coordinates,
					tubulinProfileDerivative.values,
					"distance to center",
					"d/dx spindle mask maxima along shortest DNA axis" );
			Logger.log( "Spindle max derivative plus offset " + ( tubulinExtrema.get( 0 ).coordinate ) );
			Logger.log( "Spindle max derivative minus offset " + ( tubulinExtrema.get( 1 ).coordinate ) );
		}

		return spindlePoles;
	}

	private void measureDnaDilation( ProfileAndRadius dnaLateralProfileAndRadius )
	{
		final double dnaLateralRadialProfileMaxIntensity =
				CurveAnalysis.maximumIndexAndValue( dnaLateralProfileAndRadius.profile ).value;

		final double dnaCenterIntensity = dnaLateralProfileAndRadius.profile.values.get( 0 );

		measurements.chromatinDilation = 1.0 -
				1.0 * dnaCenterIntensity / dnaLateralRadialProfileMaxIntensity;
	}

	private ArrayList< double[] > refineSpindlePoles(
			final RandomAccessibleInterval< R > dnaAlignedTubulin,
			RandomAccessibleInterval< BitType > dnaAlignedSpindleMask,
			final ArrayList< double[] > dnaAlignedSpindlePoles,
			double axialPoleRefinementRadius,
			double lateralPoleRefinementRadius )
	{
		final double blurSigma = 0.75; // micrometer

		final RandomAccessibleInterval< R > blurred = Utils.createBlurredRai(
				dnaAlignedTubulin,
				blurSigma,
				settings.voxelSizeForAnalysis );

		// typically wider perpendicular to spindle axis, narrow along spindle axis
		final RectangleShape2 rectangleShape2 = new RectangleShape2( new long[]{
				( long ) ( 2.0 * lateralPoleRefinementRadius / settings.voxelSizeForAnalysis ),
				( long ) ( 2.0 * lateralPoleRefinementRadius / settings.voxelSizeForAnalysis ),
				( long ) ( 2.0 * axialPoleRefinementRadius / settings.voxelSizeForAnalysis )
		}, false );

		final RandomAccessible< Neighborhood < R > > neighborhoodsAccessible = rectangleShape2.neighborhoodsRandomAccessible( blurred );
		final RandomAccess< Neighborhood< R > > blurredTubulinAccess = neighborhoodsAccessible.randomAccess();

		final ArrayList< double[] > spindlePoles = new ArrayList<>();

		for ( int pole = 0; pole < 2; pole++ )
		{
			final long[] pixelUnitsPolePosition = getPixelUnitsPolePosition( dnaAlignedSpindlePoles, pole );
			blurredTubulinAccess.setPosition( pixelUnitsPolePosition );
			final Neighborhood< R > blurredTubulinNeighborhood = blurredTubulinAccess.get();

			final RealPoint maximumLocation = Spindle3DAlgorithms.getMaximumLocationWithinMask(
					blurredTubulinNeighborhood,
					dnaAlignedSpindleMask,
					Utils.as3dDoubleArray( settings.voxelSizeForAnalysis ) );

			spindlePoles.add( new double[]{
					maximumLocation.getDoublePosition( 0 ),
					maximumLocation.getDoublePosition( 1 ),
					maximumLocation.getDoublePosition( 2 )} );
		}

		return spindlePoles;
	}

	private long[] getPixelUnitsPolePosition( ArrayList< double[] > dnaAlignedSpindlePoles, int pole )
	{
		final double[] polePosition = toVoxels( dnaAlignedSpindlePoles.get( pole ) );
		return Utils.asLongs( polePosition );
	}

	private RandomAccessibleInterval< BitType > createSpindleMask(
			RandomAccessibleInterval< R > tubulin,
			double spindleThreshold )
	{
		final RandomAccessibleInterval< BitType > mask
				= Algorithms.createMask( tubulin, spindleThreshold );

		final ImgLabeling< Integer, IntType > imgLabeling
				= Regions.asImgLabeling( mask, ConnectedComponents.StructuringElement.FOUR_CONNECTED );

		final Set< LabelRegion< Integer > > centralRegions = Regions.getCentralRegions(
				imgLabeling,
				new double[]{ 0, 0, 0 },
				( long ) ( settings.spindleFragmentInclusionZone / settings.voxelSizeForAnalysis ) );

		final RandomAccessibleInterval< BitType > centralRegionsMask =
				Regions.asMask( centralRegions,
						Intervals.dimensionsAsLongArray( mask ),
						Intervals.minAsLongArray( mask ));

		return centralRegionsMask;
	}

	private RandomAccessibleInterval< BitType > createSpindleMask00(
			RandomAccessibleInterval< R > poleToPoleAlignedSpindle,
			double spindleThreshold )
	{
		final RandomAccessibleInterval< BitType > mask =
				Utils.createEmptyMask( poleToPoleAlignedSpindle );

		final Cursor< R > cursor =
				Views.iterable( poleToPoleAlignedSpindle ).localizingCursor();

		final RandomAccess< BitType > access = mask.randomAccess();

		long[] position = new long[ 3 ];

		final double maximumPerpendicularAxisDistanceSquared = Math.pow(
				measurements.metaphasePlateLength / 2.0, 2 );
		final double maximumAlongAxisDistance = 1.1 *
				measurements.spindleLength / 2.0;

		while ( cursor.hasNext() )
		{
			cursor.next();
			cursor.localize( position );

			final double perpendicularAxisDistanceSquared =
					Math.pow( position[ 0 ] * settings.voxelSizeForAnalysis, 2 ) +
							Math.pow( position[ 1 ] * settings.voxelSizeForAnalysis, 2 );

			if ( perpendicularAxisDistanceSquared > maximumPerpendicularAxisDistanceSquared )
				continue;

			final double alongAxisDistance =
					Math.abs( position[ 2 ] * settings.voxelSizeForAnalysis );

			if ( alongAxisDistance > maximumAlongAxisDistance )
				continue;

			if ( cursor.get().getRealDouble() > spindleThreshold )
			{
				access.setPosition( cursor );
				access.get().set( true );
			}

		}
		return mask;
	}

	private RandomAccessibleInterval< R > createTransformedMultiChannelOutputImage(
			RandomAccessibleInterval< BitType > spindleAlignedDnaMask,
			RandomAccessibleInterval< BitType > spindleAlignedSpindleMask,
			AffineTransform3D rescaledInputToSpindleAlignedTransform,
			RandomAccessibleInterval< BitType > spindleAlignedInterestPointsImage,
			FinalInterval crop )
	{
		final ArrayList< RandomAccessibleInterval< R > > alignedVolumes = new ArrayList<>();

		for ( int c = 0; c < rescaledVolumes.size(); c++ )
		{
			final RandomAccessible transformedRA = Transforms.createTransformedRaView( rescaledVolumes.get( c ),
					rescaledInputToSpindleAlignedTransform, new ClampingNLinearInterpolatorFactory() );

			alignedVolumes.add( Views.interval( transformedRA, crop ) );
		}

		addMask( alignedVolumes, spindleAlignedDnaMask, crop );
		addMask( alignedVolumes, spindleAlignedSpindleMask, crop );
		addMask( alignedVolumes, spindleAlignedInterestPointsImage, crop );

		// The volumes are now aligned such that the spindle is along the z-axis
		// We rotate it now to be along the x-axis to make it easier to view in ImageJ
		final ArrayList< RandomAccessibleInterval< R > > alignedAndRotatedVolumes = new ArrayList<>();
		for ( RandomAccessibleInterval< R > rai : alignedVolumes )
			alignedAndRotatedVolumes.add( Views.rotate( rai, 0, 2 ) );

		final RandomAccessibleInterval< R > stack = Views.stack( alignedAndRotatedVolumes );

		return stack;
	}

	private void addMask( ArrayList< RandomAccessibleInterval< R > > alignedVolumes, RandomAccessibleInterval< BitType > mask, FinalInterval crop )
	{
		alignedVolumes.add(
				convertBitTypeToUnsignedByteOrShortType( Views.interval( Views.extendValue( mask, false  ), crop ), Util.getTypeFromInterval( alignedVolumes.get( 0 ) ) ) );
	}

	private RandomAccessibleInterval< R > convertBitTypeToUnsignedByteOrShortType( RandomAccessibleInterval< BitType > spindleAlignedDnaMask, R type )
	{
		if ( type instanceof UnsignedByteType )
		{
			return ( RandomAccessibleInterval ) Converters.convert(
					spindleAlignedDnaMask,
					( i, o ) -> o.set( i.getRealDouble() > 0 ? 255 : 0 ),
					new UnsignedByteType() );
		}
		else
		{
			return ( RandomAccessibleInterval ) Converters.convert(
					spindleAlignedDnaMask,
					( i, o ) -> o.set( i.getRealDouble() > 0 ? 65535 : 0 ),
					new UnsignedShortType() );
		}
	}

	private AffineTransform3D createOutputImageAffineTransform3D( AffineTransform3D alignmentTransform )
	{
		AffineTransform3D rotation = alignmentTransform.copy(); // "rotation" rotates the data such that the spindle axis is the zAxis
		rotation.setTranslation( new double[ 3 ] );

		final double[] zAxis = { 0, 0, 1 };

		// compute the spindle axis in the coordinate system of the input data
		final double[] spindleAxis = new double[ 3 ];
		rotation.inverse().apply( zAxis, spindleAxis );

		// set z-component of spindleAxis to zero, because we want to rotate parallel to coverslip
		spindleAxis[ Spindle3DMeasurements.ALIGNED_DNA_AXIS ] = 0;

		// compute rotation vector
		// note: we do not know in which direction of the spindle the spindleAxis vector points,
		// but we think it does not matter for alignment to the x-axis
		final double angleBetweenXAxisAndSpindleWithVector = angleOfSpindleAxisToXAxisInRadians( spindleAxis );

		AffineTransform3D rotationTransform = new AffineTransform3D();
		rotationTransform.rotate( Spindle3DMeasurements.ALIGNED_DNA_AXIS, angleBetweenXAxisAndSpindleWithVector );

		Logger.log( "Rotating input data around z-axis by [degrees]: " + 180 / Math.PI * angleBetweenXAxisAndSpindleWithVector );

		return rotationTransform;
	}

	private RandomAccessibleInterval< BitType > createInterestPointImage(
			ArrayList< double[] > spindlePoles,
			RandomAccessibleInterval< R > referenceImage )
	{
		RandomAccessibleInterval< BitType > interestPointsImage = ArrayImgs.bits( Intervals.dimensionsAsLongArray( referenceImage ) );
		interestPointsImage = Views.translate( interestPointsImage, Intervals.minAsLongArray( referenceImage ) );

		final double[] origin = { 0, 0, 0 };
		drawPoint(
				interestPointsImage,
				origin,
				settings.interestPointsRadius,
				settings.voxelSizeForAnalysis,
				new BitType( true ) );

		final double distance = LinAlgHelpers.distance( spindlePoles.get( 0 ), spindlePoles.get( 1 ) );

		for ( double[] spindlePole : spindlePoles )
			drawPoint(
					interestPointsImage,
					spindlePole,
					settings.interestPointsRadius,
					settings.voxelSizeForAnalysis,
					new BitType( true ) );

		return interestPointsImage;
	}

	public void setCellMask( RandomAccessibleInterval< BitType > cellMask )
	{
		this.cellMask = cellMask;
	}

	class ProfileAndRadius
	{
		CoordinatesAndValues profile;
		Double radius;
		int radiusIndex;
	}

	/**
	 *
	 * TODO: Also limit the computations axially?!
	 *
	 *
	 * @param image
	 * @param name
	 * @param derivativeDelta
	 * @param maxCenterDistance
	 * @return
	 */
	private ProfileAndRadius measureRadialProfileAndRadius(
			final RandomAccessibleInterval< R > image,
			final String name,
			double derivativeDelta,
			double maxCenterDistance )
	{
		// config
		final double[] center = { 0, 0 };
		final double spacing = settings.voxelSizeForAnalysis;

		// measure
		final ProfileAndRadius profileAndRadius = new ProfileAndRadius();
		profileAndRadius.profile = Algorithms.computeRadialProfile( image, center, spacing, maxCenterDistance );

		final CoordinatesAndValues radialProfileDerivative =
				CurveAnalysis.derivative(
						profileAndRadius.profile,
						(int) Math.ceil( derivativeDelta / settings.voxelSizeForAnalysis ) );

		profileAndRadius.radius = CurveAnalysis.minimum( radialProfileDerivative ).coordinate;
		profileAndRadius.radiusIndex = (int) ( profileAndRadius.radius / spacing );

 		if ( settings.showIntermediatePlots )
		{
			show( image, "DNA maximum projection", null, voxelSizesForAnalysis, false );

			Plots.plot( profileAndRadius.profile,
					"center distance [um]", name + " intensity" );

			Plots.plot( radialProfileDerivative,
					"center distance [um]", "d/dx " + name + " intensity" );
		}

		return profileAndRadius;
	}

	public CompositeImage createOutputImage()
	{
		final AffineTransform3D rescaledInputToSpindleAlignedTransform =
				rescaledToDnaAlignmentTransform.preConcatenate( dnaAlignedToSpindleAlignedTransform );

		final int size = (int) ( settings.cellRadius / settings.voxelSizeForAnalysis );

		final int[] center = { 0, 0, 0 };
		final FinalInterval crop = FinalInterval.createMinMax(
				center[ 0 ] - 3 * size,
				center[ 1 ] - 3 * size,
				center[ 2 ] - 3 * size, // spindle axis
				center[ 0 ] + 3 * size,
				center[ 1 ] + 3 * size,
				center[ 2 ] + 3 * size // spindle axis
		);

		RandomAccessibleInterval< R > raiXYZC = createTransformedMultiChannelOutputImage(
				spindleAlignedDnaMask,
				spindleAlignedSpindleMask,
				rescaledInputToSpindleAlignedTransform,
				createInterestPointImage( spindleAlignedSpindlePoles, spindleAlignedDna ),
				crop );

		if ( settings.showIntermediateImages )
		{
			BdvFunctions.show( raiXYZC, "output", BdvOptions.options().axisOrder( AxisOrder.XYZC ) );
		}

		final ImagePlus imp = ImageJFunctions.wrap( Views.permute( raiXYZC, 2, 3 ), "output" );

		final Calibration calibration = new Calibration();
		calibration.pixelHeight = settings.voxelSizeForAnalysis;
		calibration.pixelWidth = settings.voxelSizeForAnalysis;
		calibration.pixelDepth = settings.voxelSizeForAnalysis;
		calibration.setUnit( "micrometer" );
		imp.setCalibration( calibration );

		final CompositeImage compositeImage = new CompositeImage( imp );
		compositeImage.setDisplayMode( CompositeImage.COMPOSITE );
		compositeImage.setZ( compositeImage.getNSlices() / 2 );

		int contrastLimit = 255;
		if ( compositeImage.getBitDepth() == 16 )
			contrastLimit = 65535;

		final int numChannels = (int) rescaledVolumes.size();

		// Color channels
		for ( int c = 0; c < numChannels; c++ )
		{
			compositeImage.setC( c + 1 );
			if ( c == ( int ) settings.dnaChannelIndex )
				IJ.run( compositeImage, "Blue", "" );
			else if ( c == ( int ) settings.tubulinChannelIndex )
 				IJ.run( compositeImage, "Green", "" );
			else
				IJ.run( compositeImage, "Grays", "" );

			IJ.run( compositeImage,"Enhance Contrast", "saturated=0.35");
		}

		int lastChannelIndex = numChannels;

		// Binary masks
		compositeImage.setC( ++lastChannelIndex );
		IJ.run( compositeImage, "Blue", "" );
		compositeImage.setDisplayRange( 0, 2 * contrastLimit ); // 2 * contrastLimit to make it appear transparent

		compositeImage.setC( ++lastChannelIndex );
		IJ.run( compositeImage, "Green", "" );
		compositeImage.setDisplayRange( 0, 2 * contrastLimit );

		// Interest points
		compositeImage.setC( ++lastChannelIndex  );
		IJ.run( compositeImage, "Grays", "" );
		compositeImage.setDisplayRange( 0, 2 * contrastLimit );

		return compositeImage;
	}

	private < T extends Type< T > >  void drawTransformedPoint(
			AffineTransform3D alignmentTransform,
			RandomAccessibleInterval< T > interestPointsImage,
			double[] point,
			T value )
	{
		final double[] transformedPoint = transformToCoverslipCoordinateSystem( alignmentTransform, point );

		drawPoint(
				interestPointsImage,
				transformedPoint,
				settings.interestPointsRadius,
				settings.voxelSizeForAnalysis,
				value );
	}

	public HashMap< Integer, Map< String, Object > > getObjectMeasurements()
	{
		return objectMeasurements;
	}

	private double[] transformToCoverslipCoordinateSystem(
			AffineTransform3D alignmentTransform,
			double[] point )
	{
		final double[] transformedPoint = Utils.copy( point );

		alignmentTransform.inverse().apply( point, transformedPoint ); // transform back

		return transformedPoint;
	}

	private < T extends Type< T > > void drawPoint(
			RandomAccessibleInterval< T > rai,
			double[] position,
			double calibratedRadius,
			double calibration,
			T value )
	{
		Shape shape = new HyperSphereShape( (int) Math.ceil( calibratedRadius / calibration ) );
		final RandomAccessible< Neighborhood< T > > nra = shape.neighborhoodsRandomAccessible( rai );
		final RandomAccess< Neighborhood< T > > neighborhoodRandomAccess = nra.randomAccess();

		final double[] pixelPositionDoubles = Arrays.stream( position ).map( x -> x / calibration ).toArray();
		final long[] pixelPositionLongs = Utils.asRoundedLongs( pixelPositionDoubles );
		neighborhoodRandomAccess.setPosition( pixelPositionLongs );
		final Neighborhood< T > neighborhood = neighborhoodRandomAccess.get();

		final long[] pos = new long[ 3 ];
		final Cursor< T > cursor = neighborhood.cursor();
		while( cursor.hasNext() )
		{
			try
			{
				cursor.fwd();
				cursor.localize( pos );
				cursor.get().set( value );
			}
			catch ( ArrayIndexOutOfBoundsException e )
			{
				Logger.log( "[ERROR] Draw points out of bounds..." );
				break;
			}
		}
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< BitType > createMask(
			RandomAccessibleInterval< T > rai,
			double threshold,
			OpService opService )
	{
		RandomAccessibleInterval< BitType > mask
				= Converters.convert( rai, ( i, o ) ->
				o.set( i.getRealDouble() > threshold ? true : false ), new BitType() );

		// "Bug" in ops requires a Views.zeroMin().
		mask = opService.morphology().fillHoles( Views.zeroMin( mask ) );

		mask = Transforms.getWithAdjustedOrigin( rai, mask );

		return mask;
	}
}
