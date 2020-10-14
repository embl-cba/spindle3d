import de.embl.cba.spindle3d.Spindle3DCommand;
import de.embl.cba.tables.FileUtils;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.RealType;

import java.io.File;
import java.util.List;

public class RunSpindleMorphometryBatch
{
	public static < R extends RealType< R > > void main( String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		final Spindle3DCommand< R > command = new Spindle3DCommand<>();
		command.opService = ij.op();

		List< File > fileList = FileUtils.getFileList( new File( "/Users/tischer/Desktop/kletter/spindle-test-data" ), ".*", false );

		for ( File file : fileList )
		{
			command.inputImageFile = file;
			command.outputDirectory = new File( "/Users/tischer/Desktop/kletter" );
			command.spindleChannelIndexOneBased = 2;  // normally 1
			command.dnaChannelIndexOneBased = 1; // normally 2
			command.showIntermediateResults = false;
			command.saveResults = true;
			command.run();
		}

	}
}
