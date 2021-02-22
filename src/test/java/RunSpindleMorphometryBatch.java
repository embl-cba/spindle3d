import de.embl.cba.spindle3d.Spindle3DAdvancedCommand;
import de.embl.cba.spindle3d.Spindle3DCommand;
import de.embl.cba.spindle3d.Spindle3DFileCommand;
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

		final Spindle3DFileCommand< R > command = new Spindle3DFileCommand<>();
		command.opService = ij.op();

		List< File > fileList = FileUtils.getFileList( new File( "/Users/tischer/Documents/spindle3d/src/test/resources/multiple-dna" ), ".*", false );

		for ( File file : fileList )
		{
			if ( ! file.getName().endsWith( ".tif" ) ) continue;
			command.inputImageFile = file;
			command.outputDirectory = new File( "/Users/tischer/Desktop/Desktop/kletter/multiple-dna" );
			command.dnaChannelIndexOneBased = 1;
			command.spindleChannelIndexOneBased = 2;
			command.showIntermediateImages = false;
			command.saveResults = true;
			command.run();
		}
	}
}
