package de.embl.cba.spindle3d;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.embl.cba.spindle3d.util.Utils;
import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class Spindle3DMeasurements
{

	public static final String CENTROID = "Centroid";

	public static final String VOLUME = "Volume";
	public static final String AREA = "Area";
	public static final String LENGTH = "Length";

	public static final String PERIMETER = "Perimeter";
	public static final String SURFACE = "Surface";

	public static final String PIXEL_UNIT = "Pixel";
	public static final String POW = ""; // the ^ character felt to risky

	public static final String SUM_INTENSITY = "SumIntensity";
	public static final String IMAGE_BOUNDARY_CONTACT = "ImageBoundaryContact";

	public static final String GLOBAL_BACKGROUND_INTENSITY = "GlobalBackgroundIntensity";
	public static final String SKELETON_TOTAL_LENGTH = "SkeletonTotalLength";
	public static final String SKELETON_NUMBER_BRANCH_POINTS = "SkeletonNumBranchPoints";
	public static final String SKELETON_AVG_BRANCH_LENGTH = "SkeletonAvgBranchLength";
	public static final String SKELETON_LONGEST_BRANCH_LENGTH = "SkeletonLongestBranchLength";

	public static final String SEP = "_";
	public static final String FRAME_UNITS = "Frames";
	public static final String TIME = "Time";
	public static final String VOXEL_SPACING = "VoxelSpacing";
	public static final String FRAME_INTERVAL = "FrameInterval";
	public static final String BRIGHTEST_POINT = "BrightestPoint";
	public static final String RADIUS_AT_BRIGHTEST_POINT = "RadiusAtBrightestPoint";
	public static final String CONVEX_AREA = "ConvexArea";

	public static final String[] XYZ = new String[]{"X","Y","Z"};
	public static final String LENGTH_UNIT = "um";
	public static final String AREA_UNIT = "um2";
	public static final String VOLUME_UNIT = "um3";

	public static final int ALIGNED_DNA_AXIS = 2;
	public static final String ANALYSIS_FINISHED = "Analysis finished.";

	// define constants to be accessible in the tests
	public static final String SPINDLE_LENGTH = addLengthUnit( "Spindle_Length" );
	public static final String SPINDLE_WIDTH_AVG = addLengthUnit( "Spindle_Width_Avg" );
	public static final String SPINDLE_ANGLE_DEGREES = "Spindle_Angle_Degrees";

	public String version;
	public Double dnaThreshold = Double.NaN;
	public Double metaphasePlateLength = Double.NaN;
	public Double metaphasePlateWidth = Double.NaN;
	public Double chromatinVolume = Double.NaN;
	public Double chromatinDilation = Double.NaN;
	public Double spindleLength = Double.NaN;
	public Double spindleThreshold = Double.NaN;
	public Double spindleSNR = Double.NaN;
	public Double spindleVolume = Double.NaN;
	public Double spindleWidthMin = Double.NaN;
	public Double spindleWidthMax = Double.NaN;
	public Double spindleCenterToMetaphasePlateCenterDistance = Double.NaN;
	public Double spindleAngle = Double.NaN;
	public Double tubulinSpindleIntensityVariation = Double.NaN;
	public Double tubulinSpindleAverageIntensity = Double.NaN;
	public Double tubulinCellularAverageIntensity = Double.NaN;
	public Double tubulinCytoplasmAverageIntensity = Double.NaN;
	public Double spindleWidthAvg = Double.NaN;
	public Double spindleAspectRatio = Double.NaN;
	public Double cellVolume = Double.NaN;
	public Double cellSurface = Double.NaN;
    public String log = "";

	private final transient HashMap< Integer, Map< String, Object > > objectMeasurements;

	public Spindle3DMeasurements( HashMap< Integer, Map< String, Object > > objectMeasurements )
	{
		this.objectMeasurements = objectMeasurements;
	}

	public void setMeasurementsForExport( )
	{
		IJ.log( this.toString() );

		add( "Version", version );

		add( "DNA_Threshold", dnaThreshold );

		add( addLengthUnit( "MetaphasePlate_Width" ), metaphasePlateWidth );

		add( addLengthUnit( "MetaphasePlate_Length" ), metaphasePlateLength );

		add( addVolumeUnit( "Chromatin_Volume" ), chromatinVolume );

		add( "Chromatin_Dilation", chromatinDilation );

		add( "Tubulin_Spindle_Intensity_Threshold",  spindleThreshold );

		add( "Tubulin_Spindle_Intensity_Variation", tubulinSpindleIntensityVariation );

		add( "Tubulin_Spindle_Average_Intensity", tubulinSpindleAverageIntensity );

		add( "Tubulin_Cellular_Avg_Intensity", tubulinCellularAverageIntensity );

		add( "Spindle_SNR", spindleSNR );

		add( "Spindle_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT, spindleVolume );

		add( SPINDLE_LENGTH, spindleLength );

		add( addLengthUnit( "Spindle_Width_Min" ), spindleWidthMin );

		add( addLengthUnit( "Spindle_Width_Max" ), spindleWidthMax );

		add( SPINDLE_WIDTH_AVG, spindleWidthAvg );

		add( "Spindle_Aspect_Ratio", spindleAspectRatio );

		add( addLengthUnit( "Spindle_Center_To_MetaphasePlate_Center_Distance" ), spindleCenterToMetaphasePlateCenterDistance );

		add( SPINDLE_ANGLE_DEGREES, spindleAngle );

		add( getCellVolumeMeasurementName(), cellVolume );

		add( getCellSurfaceMeasurementName(), cellSurface );

		add( "Comment", log );
	}

	@NotNull
	public static String getCellVolumeMeasurementName()
	{
		return "Cell_Volume" + SEP + Spindle3DMeasurements.VOLUME_UNIT;
	}

	@NotNull
	public static String getCellSurfaceMeasurementName()
	{
		return "Cell_Surface" + SEP + Spindle3DMeasurements.AREA_UNIT;
	}

	private void add( String name, Object value )
	{
		addMeasurement( objectMeasurements, 0, name, value );
	}

	public static void addMeasurement(
			HashMap< Integer, Map< String, Object > > objectMeasurements,
			int objectLabel, String name, Object value )
	{
		if ( ! objectMeasurements.containsKey( objectLabel ) )
			objectMeasurements.put( objectLabel, new HashMap<>(  ) );

		objectMeasurements.get( objectLabel ).put( name, value );
	}

	public static String addLengthUnit( String name )
	{
		return name + SEP + LENGTH_UNIT;
	}

	public static String addVolumeUnit( String name )
	{
		return name + SEP + VOLUME_UNIT;
	}

	public String toString()
	{
		String s = "\n## Spindle3D Measurements\n";
		Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
		s += gson.toJson( this );
		s += "\n";
		return s;
	}

	public static JTable asTable( HashMap< Integer, Map< String, Object > > objectMeasurements )
	{
		final ArrayList< HashMap< Integer, Map< String, Object > > > timepoints = new ArrayList<>();
		timepoints.add( objectMeasurements );
		return Utils.createJTableFromStringList( measurementsAsTableRowsStringList( timepoints, "\t" ), "\t" );
	}

	public static JTable asTable(
			ArrayList< HashMap< Integer, Map< String, Object > > > timepoints )
	{
		return Utils.createJTableFromStringList(
				measurementsAsTableRowsStringList( timepoints, "\t" ),
				"\t" );
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


	public static ArrayList< String > measurementsAsTableRowsStringList(
			ArrayList< HashMap< Integer,
			Map< String, Object > > > measurementsTimePointList,
			String delim )
	{

		final Set< Integer > objectLabelsFirstTimePoint =
				measurementsTimePointList.get( 0 ).keySet();

		final Set< String > measurementSet =
				measurementsTimePointList.get( 0 ).get(
						objectLabelsFirstTimePoint.iterator().next() ).keySet();

		List< String  > measurementNames = new ArrayList< String >( measurementSet );
		Collections.sort( measurementNames );

		final ArrayList< String > lines = new ArrayList<>();

		String header = "Object_Label";
		header += delim + CENTROID + SEP + TIME + SEP + FRAME_UNITS;
		for ( String measurementName : measurementNames )
			header += delim + measurementName ;

		lines.add( header );

		for ( int t = 0; t < measurementsTimePointList.size(); ++t )
		{
			final HashMap< Integer, Map< String, Object > > measurements
					= measurementsTimePointList.get( t );

			final Set< Integer > objectLabels = measurements.keySet();

			for ( int label : objectLabels )
			{
				final Map< String, Object > measurementsMap = measurements.get( label );

				String values = String.format( "%05d", label );

				values += delim + String.format( "%05d", t + 1 ); // convert to one-based time points

				for ( String measurementName : measurementNames )
					values += delim + measurementsMap.get( measurementName );

				lines.add( values );
			}
		}

		return lines;
	}
}
