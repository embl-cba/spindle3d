package de.embl.cba.spindle3d.util;

import ij.ImagePlus;
import ij.measure.Calibration;
import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import net.imglib2.*;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static de.embl.cba.spindle3d.Spindle3DAlgorithms.getWithAdjustedOrigin;

public class Utils
{

	public enum BorderExtension
	{
		ExtendZero,
		ExtendBorder,
		ExtendMirror
	}

	public static AffineTransform3D getRotationTransform3D(
			double[] normalisedTargetAxis,
			double[] normalisedAxis )
	{

		// Note that for the dot-product the order of the vectors does not matter.
		double rotationAngle = Math.acos( LinAlgHelpers.dot( normalisedAxis, normalisedTargetAxis ) );

		if ( rotationAngle == 0.0 ) return new AffineTransform3D();

		// Note that here, for the cross-product, the order of the vectors is important!
		double[] rotationAxis = new double[3];
		LinAlgHelpers.cross( normalisedAxis, normalisedTargetAxis, rotationAxis );

		if ( LinAlgHelpers.length( rotationAxis ) == 0.0 )
		{
			// Since the rotation angle is not zero (see above), the vectors are anti-parallel.
			// This means that the cross product does not work for finding a perpendicular vector.
			// It also means we need to rotate 180 degrees around any axis that
			// is perpendicular to any of the two vectors.
			// That means that the dot-product of the rotation axis and any
			// of the two vectors should be zero:
			// u * x + v * y + w * z != 0

			rotationAxis = VectorUtils.getPerpendicularVector( normalisedAxis );

		}

		LinAlgHelpers.normalize( rotationAxis );

		final double[] q = new double[ 4 ];
		LinAlgHelpers.quaternionFromAngleAxis( rotationAxis, rotationAngle, q );

		final double[][] m = new double[ 3 ][ 4 ];
		LinAlgHelpers.quaternionToR( q, m );

		AffineTransform3D rotation = new AffineTransform3D();
		rotation.set( m );

		return rotation;
	}

	public static FinalInterval asIntegerInterval( FinalRealInterval realInterval )
	{
		double[] realMin = new double[ 3 ];
		double[] realMax = new double[ 3 ];
		realInterval.realMin( realMin );
		realInterval.realMax( realMax );

		long[] min = new long[ 3 ];
		long[] max = new long[ 3 ];

		for ( int d = 0; d < min.length; ++d )
		{
			min[ d ] = (long) realMin[ d ];
			max[ d ] = (long) realMax[ d ];
		}

		return new FinalInterval( min, max );
	}


	public static < R extends RealType< R > & NativeType< R > >
	double computeCoefficientOfVariation(
			RandomAccessibleInterval< R > intensities,
			RandomAccessibleInterval< BitType > mask,
			Double meanOffset )
	{
		final ArrayList< Double > intensitiesWithMask =
				getValuesWithinMaskAsList( intensities, mask );

		final double mean = mean( intensitiesWithMask );
		final double sdev = sdev( intensitiesWithMask, mean );

		double cov = sdev / ( mean - meanOffset );

		return cov;
	}

	public static double mean( List<Double> values ){
		double sum = sum( values );
		double mean = 0;
		mean = sum / ( values.size() * 1.0 );
		return mean;
	}

	public static double sdev( List<Double> values, Double mean ){
		double sdev = 0;
		for( double v : values )
			sdev += ( v - mean ) * ( v - mean );
		sdev /= ( values.size() * 1.0 );
		sdev = Math.sqrt( sdev );
		return sdev;
	}

	public static < T extends RealType< T > & NativeType< T > >
	double computeAverage( final RandomAccessibleInterval< T > rai,
						   final RandomAccessibleInterval< BitType > mask )
	{
		final Cursor< BitType > cursor = Views.iterable( mask ).cursor();
		final RandomAccess< T > randomAccess = rai.randomAccess();

		randomAccess.setPosition( cursor );

		double average = 0;
		long n = 0;

		while ( cursor.hasNext() )
		{
			if ( cursor.next().get() )
			{
				randomAccess.setPosition( cursor );
				average += randomAccess.get().getRealDouble();
				++n;
			}
		}

		average /= n;

		return average;
	}

	public static double mad( List<Double> values, Double median )
	{
		final ArrayList< Double > absoluteDeviations = new ArrayList<>();

		for ( Double value : values )
		{
			absoluteDeviations.add( Math.abs( value - median ) );
		}

		return median( absoluteDeviations );
	}


	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView( RandomAccessibleInterval< T > rai,
													InvertibleRealTransform combinedTransform,
													InterpolatorFactory interpolatorFactory)
	{
		final RandomAccessible transformedRA = createTransformedRaView( rai, combinedTransform, interpolatorFactory );
		final FinalInterval transformedInterval = createBoundingIntervalAfterTransformation( rai, combinedTransform );
		final RandomAccessibleInterval< T > transformedIntervalView = Views.interval( transformedRA, transformedInterval );

		return transformedIntervalView;
	}

	public static FinalInterval createBoundingIntervalAfterTransformation( Interval interval, InvertibleRealTransform transform )
	{
		List< long[ ] > corners = Corners.corners( interval );

		long[] boundingMin = new long[ interval.numDimensions() ];
		long[] boundingMax = new long[ interval.numDimensions() ];
		Arrays.fill( boundingMin, Long.MAX_VALUE );
		Arrays.fill( boundingMax, Long.MIN_VALUE );

		for ( long[] corner : corners )
		{
			double[] transformedCorner = transformedCorner( transform, corner );

			adjustBoundingRange( boundingMin, boundingMax, transformedCorner );
		}

		return new FinalInterval( boundingMin, boundingMax );
	}

	private static void adjustBoundingRange( long[] min, long[] max, double[] transformedCorner )
	{
		for ( int d = 0; d < transformedCorner.length; ++d )
		{
			if ( transformedCorner[ d ] > max[ d ] )
			{
				max[ d ] = (long) transformedCorner[ d ];
			}

			if ( transformedCorner[ d ] < min[ d ] )
			{
				min[ d ] = (long) transformedCorner[ d ];
			}
		}
	}

	private static double[] transformedCorner( InvertibleRealTransform transform, long[] corner )
	{
		double[] cornerAsDouble = Arrays.stream( corner ).mapToDouble( x -> x ).toArray();
		double[] transformedCorner = new double[ corner.length ];
		transform.apply( cornerAsDouble, transformedCorner );
		return transformedCorner;
	}


	public static void openURI( String uri )
	{
		try
		{
			java.awt.Desktop.getDesktop().browse( new URI( uri ));
		} catch ( IOException e )
		{
			e.printStackTrace();
		} catch ( URISyntaxException e )
		{
			e.printStackTrace();
		}
	}

	public static ImagePlus openWithBioFormats( File file )
	{
		return openWithBioFormats( file.getAbsolutePath() );
	}

	public static ImagePlus openWithBioFormats( String path )
	{
		try
		{
			ImporterOptions opts = new ImporterOptions();
			opts.setId( path );
			opts.setVirtual( true );
			ImportProcess process = new ImportProcess( opts );
			process.execute();
			ImagePlusReader impReader = new ImagePlusReader( process );
			ImagePlus[] imps = impReader.openImagePlus();
			return imps[ 0 ];
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}

	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView( RandomAccessibleInterval< T > rai,
													InvertibleRealTransform combinedTransform,
													InterpolatorFactory interpolatorFactory,
													BorderExtension borderExtension)
	{
		final RandomAccessible transformedRA = createTransformedRaView( rai, combinedTransform, interpolatorFactory, borderExtension );
		final FinalInterval transformedInterval = createBoundingIntervalAfterTransformation( rai, combinedTransform );
		final RandomAccessibleInterval< T > transformedIntervalView = Views.interval( transformedRA, transformedInterval );

		return transformedIntervalView;
	}

	public static < T extends NumericType< T > >
	RandomAccessible createTransformedRaView(
			RandomAccessibleInterval< T > rai,
			InvertibleRealTransform combinedTransform,
			InterpolatorFactory interpolatorFactory,
			BorderExtension borderExtension )
	{
		ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > source = createExtendedRai( rai, borderExtension );
		RealRandomAccessible rra = Views.interpolate( source, interpolatorFactory );
		rra = RealViews.transform( rra, combinedTransform );
		return Views.raster( rra );
	}

	private static < T extends NumericType< T > > ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > createExtendedRai( RandomAccessibleInterval< T > rai, BorderExtension borderExtension )
	{
		switch ( borderExtension )
		{
			case ExtendZero:
				return Views.extendZero( rai );
			case ExtendBorder:
				return Views.extendBorder( rai );
			case ExtendMirror:
				return Views.extendMirrorDouble( rai );
			default:
				return Views.extendZero( rai );
		}
	}

	public static JTable createJTableFromStringList( List< String > strings, String delim )
	{
		delim = autoDelim( delim, strings );

		StringTokenizer st = new StringTokenizer( strings.get( 0 ), delim );

		List< String > colNames = new ArrayList<>();

		while ( st.hasMoreTokens() )
			colNames.add( st.nextToken() );

		/**
		 * Init model and columns
		 */

		ColumnClassAwareTableModel model = new ColumnClassAwareTableModel();

		for ( String colName : colNames )
			model.addColumn( colName );

		int numCols = colNames.size();

		/**
		 * Add tablerow entries
		 */

		for ( int iString = 1; iString < strings.size(); ++iString )
		{
			model.addRow( new Object[ numCols ] );

			st = new StringTokenizer( strings.get( iString ), delim );

			for ( int iCol = 0; iCol < numCols; iCol++ )
			{
				String stringValue = st.nextToken();

				try
				{
					final Double numericValue = Utils.parseDouble( stringValue );
					model.setValueAt( numericValue, iString - 1, iCol );
				} catch ( Exception e )
				{
					model.setValueAt( stringValue, iString - 1, iCol );
				}
			}

		}

		model.refreshColumnClassesFromObjectColumns();

		return new JTable( model );
	}

	public static Double parseDouble( String cell )
	{
		if ( cell.equalsIgnoreCase( "nan" )
				|| cell.equalsIgnoreCase( "na" )
				|| cell.equals( "" ) )
			return Double.NaN;
		else if ( cell.equalsIgnoreCase( "inf" ) )
			return Double.POSITIVE_INFINITY;
		else if ( cell.equalsIgnoreCase( "-inf" ) )
			return Double.NEGATIVE_INFINITY;
		else
			return Double.parseDouble( cell );
	}

	public static void saveTable( JTable table, File tableOutputFile )
	{
		try
		{
			saveTableWithIOException( table, tableOutputFile );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	private static void saveTableWithIOException( JTable table, File file ) throws IOException
	{
		BufferedWriter bfw = new BufferedWriter( new FileWriter( file ) );

		final int lastColumn = table.getColumnCount() - 1;

		// header
		for ( int col = 0; col < lastColumn; col++ )
			bfw.write( table.getColumnName( col ) + "\t" );
		bfw.write( table.getColumnName( lastColumn ) + "\n" );

		// content
		for ( int row = 0; row < table.getRowCount(); row++ )
		{
			for ( int col = 0; col < lastColumn; col++ )
				bfw.write( table.getValueAt( row, col ) + "\t" );
			bfw.write( table.getValueAt( row, lastColumn ) + "\n" );
		}

		bfw.close();
	}

	public static double[] getCalibration( ImagePlus imp )
	{
		double[] calibration = new double[ 3 ];

		calibration[ X ] = imp.getCalibration().pixelWidth;
		calibration[ Y ] = imp.getCalibration().pixelHeight;
		calibration[ Z ] = imp.getCalibration().pixelDepth;

		return calibration;
	}

	public static String autoDelim( String delim, List< String > strings )
	{
		if ( delim == null )
		{
			if ( strings.get( 0 ).contains( "\t" ) )
			{
				delim = "\t";
			} else if ( strings.get( 0 ).contains( "," ) )
			{
				delim = ",";
			} else if ( strings.get( 0 ).contains( ";" ) )
			{
				delim = ";";
			} else
			{
				throw new RuntimeException( "Could not identify table delimiter." );
			}

		}
		return delim;
	}

	public static void divide( double[] doubles, double factor )
	{
		for ( int i = 0; i < doubles.length; ++i )
			doubles[ i ] /= factor;
	}


	public static long countNonZeroPixelsAlongAxis(
			RandomAccessibleInterval< BitType > rai,
			int axis )
	{
		// Set position at zero
		final RandomAccess< BitType > access = rai.randomAccess();
		access.setPosition( new long[ rai.numDimensions() ] );

		long numNonZeroPixels = 0;
		for ( long coordinate = rai.min( axis ); coordinate <= rai.max( axis ); ++coordinate )
		{
			access.setPosition( coordinate, axis );
			if ( access.get().get() ) numNonZeroPixels++;
		}

		return numNonZeroPixels;
	}

	public static double getAngle( double[] v1, double[] v2 )
	{
		double le = LinAlgHelpers.length( v1 ) * LinAlgHelpers.length( v2 );
		double alpha = 0.0D;
		if (le > 0.0D) {
			double sca = LinAlgHelpers.dot( v1, v2 );
			sca /= le;
			if (sca < 0.0D) {
				sca *= -1.0D;
			}

			alpha = Math.acos(sca);
		}

		return alpha;
	}

	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView( RandomAccessibleInterval< T > rai, InvertibleRealTransform transform )
	{
		final RandomAccessible transformedRA = createTransformedRaView( rai, transform, new ClampingNLinearInterpolatorFactory() );
		final FinalInterval transformedInterval = createBoundingIntervalAfterTransformation( rai, transform );
		final RandomAccessibleInterval< T > transformedIntervalView = Views.interval( transformedRA, transformedInterval );

		return transformedIntervalView;
	}

	public static < T extends NumericType< T > >
	RandomAccessible createTransformedRaView(
			RandomAccessibleInterval< T > rai,
			InvertibleRealTransform combinedTransform,
			InterpolatorFactory interpolatorFactory )
	{
		RealRandomAccessible rra = Views.interpolate( Views.extendZero( rai ), interpolatorFactory );
		rra = RealViews.transform( rra, combinedTransform );
		return Views.raster( rra );
	}

	public static long measureSizeInPixels( RandomAccessibleInterval< BitType > mask )
	{

		final Cursor< BitType > cursor = Views.iterable( mask ).cursor();
		long size = 0;

		while ( cursor.hasNext() )
		{
			if( cursor.next().get() )
			{
				size++;
			}
		}

		return size;

	}



	public static < I extends IntegerType< I > >
	long measureSizeInPixels( RandomAccessibleInterval< I > labeling,
							  int label )
	{
		final Cursor< I > labelCursor = Views.iterable( labeling ).localizingCursor();
		long size = 0;

		while ( labelCursor.hasNext() )
		{
			long value = labelCursor.next().getInteger();

			if( value == label )
			{
				size++;
			}
		}

		return size;
	}

	public static double sum( List<Double> values ){
		if (values.size() > 0) {
			double sum = 0;
			for (Double d : values) {
				sum += d;
			}
			return sum;
		}
		return 0;
	}

	public static FinalInterval createScaledInterval( Interval interval, Scale scale )
	{
		int n = interval.numDimensions();

		long[] min = new long[ n ];
		long[] max = new long[ n ];
		interval.min( min );
		interval.max( max );

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] *= scale.getScale( d );
			max[ d ] *= scale.getScale( d );
		}

		return new FinalInterval( min, max );
	}

	public static double median( List<Double> values )
	{
		final ArrayList< Double > sorted = new ArrayList<>( values );

		Collections.sort( sorted );

		if ( sorted.size() == 1 ) return sorted.get( 0 );
		if ( sorted.size() == 0 ) return 0;

		int middle = sorted.size()/2;

		if (sorted.size() % 2 == 1) {
			return sorted.get(middle);
		} else {
			return (sorted.get(middle-1) + sorted.get(middle)) / 2.0;
		}
	}

	public static < R extends RealType< R > & NativeType< R > >
	RandomAccessibleInterval< R > createEmptyCopy( RandomAccessibleInterval< R > image )
	{
		RandomAccessibleInterval< R > copy =
				new ArrayImgFactory( Util.getTypeFromInterval( image ) ).create( image );
		copy = Views.translate( copy, Intervals.minAsLongArray( image ) );
		return copy;
	}

	public static < T extends RealType< T > & NativeType< T > >
	ArrayList< Double > getValuesWithinMaskAsList(
			final RandomAccessibleInterval< T > rai,
			final RandomAccessibleInterval< BitType > mask )
	{
		final Cursor< BitType > maskCursor = Views.iterable( mask ).cursor();
		final RandomAccess< T > intensityAccess = rai.randomAccess();

		intensityAccess.setPosition( maskCursor );

		final ArrayList< Double > doubles = new ArrayList<>();

		while ( maskCursor.hasNext() )
		{
			if ( maskCursor.next().get() )
			{
				intensityAccess.setPosition( maskCursor );
				doubles.add( intensityAccess.get().getRealDouble() );
			}
		}

		return doubles;
	}

	public static < T extends RealType< T > & NativeType< T > >
	ImgLabeling< Integer, IntType > asImgLabeling(
			RandomAccessibleInterval< T > masks,
			ConnectedComponents.StructuringElement structuringElement )
	{

		RandomAccessibleInterval< IntType > labelImg = ArrayImgs.ints( Intervals.dimensionsAsLongArray( masks ) );
		labelImg = getWithAdjustedOrigin( masks, labelImg );
		final ImgLabeling< Integer, IntType > imgLabeling = new ImgLabeling<>( labelImg );

		final java.util.Iterator< Integer > labelCreator = new java.util.Iterator< Integer >()
		{
			int id = 1;

			@Override
			public boolean hasNext()
			{
				return true;
			}

			@Override
			public synchronized Integer next()
			{
				return id++;
			}
		};

		final RandomAccessibleInterval< UnsignedIntType > unsignedIntTypeRandomAccessibleInterval =
				Converters.convert(
						masks,
						( i, o ) -> o.set( i.getRealDouble() > 0 ? 1 : 0 ),
						new UnsignedIntType() );

		ConnectedComponents.labelAllConnectedComponents(
				Views.extendBorder( unsignedIntTypeRandomAccessibleInterval ),
				imgLabeling,
				labelCreator,
				structuringElement );

		return imgLabeling;
	}


	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > getEnlargedRai( RandomAccessibleInterval< T > rai )
	{
		long[] min = new long[ 2 ];
		long[] max = new long[ 2 ];
		rai.max( max );
		for ( int d = 0; d < 2; ++d )
		{
			max[ d ] *= 1.2;
		}
		final FinalInterval interval = new FinalInterval( min, max );
		return Views.interval( Views.extendZero( rai ), interval );
	}

	public static < T extends RealType< T > & NativeType< T > >
	RandomAccessibleInterval< T > getEnlargedRai( RandomAccessibleInterval< T > rai, int border )
	{
		int n = rai.numDimensions();

		long[] min = new long[ n ];
		long[] max = new long[ n ];

		rai.min( min );
		rai.max( max );

		for ( int d = 0; d < n; ++d )
		{
			min[ d ] -= border;
			max[ d ] += border;

		}

		final FinalInterval interval = new FinalInterval( min, max );
		return Views.interval( Views.extendZero( rai ), interval );
	}


	public static ImagePlus asImagePlus( RandomAccessibleInterval rai, String title )
	{
		final ImagePlus wrap = ImageJFunctions.wrap(
				Views.permute(
						Views.addDimension( rai, 0, 0 ),
						2, 3 ), title );
		return wrap;
	}

	public static ImagePlus asImagePlus( RandomAccessibleInterval rai, String title, Calibration calibration )
	{
		final ImagePlus wrap = ImageJFunctions.wrap(
				Views.permute(
						Views.addDimension( rai, 0, 0 ),
						2, 3 ), title );

		wrap.setCalibration( calibration );
		return wrap;
	}

	public static < T extends RealType< T > >
	Double getMaximumValue( RandomAccessibleInterval< T > rai )
	{
		Cursor< T > cursor = Views.iterable( rai ).cursor();

		double maxValue = Double.MIN_VALUE;

		double value;
		while ( cursor.hasNext() )
		{
			value = cursor.next().getRealDouble();
			if ( value > maxValue )
				maxValue = value;
		}

		return maxValue;
	}


	public static double[] as3dDoubleArray( double value )
	{
		double[] array = new double[ 3 ];
		Arrays.fill( array, value );
		return array;
	}

}
