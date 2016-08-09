import Algorithm.LineConnection.MapEdge;
import Algorithm.Interpolation.DistanceFieldInterpolation;
import Algorithm.Interpolation.Serializer;
import Algorithm.LineConnection.LineWelder;
import Algorithm.NearbyGraph.NearbyContainer;
import Algorithm.NearbyGraph.NearbyEstimator;
import Algorithm.NearbyGraph.NearbyGraphWrapper;
import Algorithm.Texture.TextureGenerator;
import Deserialization.DeserializedOCAD;
import Isolines.IsolineContainer;

import java.io.*;
import java.util.ArrayList;
import java.util.stream.Stream;

import Isolines.*;
import Deserialization.Interpolation.SlopeMark;
import Utils.Constants;
import Utils.PointRasterizer;
import com.vividsolutions.jts.geom.*;

/**
 * Created by Artyom.Fomenko on 15.07.2016.
 * Parsing input, providing output, managing dataflows between classes
 */
public class MainController {

    private GeometryFactory gf;

    public IsolineContainer ic;
    public DistanceFieldInterpolation interp;

    public MapEdge edge;

    public ArrayList<SlopeMark> slopeMarks;

    public DeserializedOCAD dser;

    MainController() {
        gf = new GeometryFactory();
        ic = new IsolineContainer(gf);
        edge = null;
    }

//    public void openFile(File f) throws IOException {
//        FileInputStream fis = new FileInputStream(f);
//        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
//
//        String line = null;
//        int id = 0;
//        while ((line = br.readLine()) != null) {
//            String[] tokens = line.split("[\\s]+");
//            int type = Integer.parseInt(tokens[0]);
//            int side = Integer.parseInt(tokens[1]);
//            int n = Integer.parseInt(tokens[2]);
//
//            Coordinate[] carr = new Coordinate[n];
//            for (int i = 0; i < n; i+=1) {
//                double x = Double.parseDouble(tokens[3+i*2]);
//                double y = Double.parseDouble(tokens[3+i*2+1]);
//                carr[i] = new Coordinate(x,y);
//            }
//            CoordinateSequence cs = gf.getCoordinateSequenceFactory().create(carr);
//            Isoline is = new Isoline( type, side, cs, gf);
//            id += 1;
//            ic.add( is );
//        }
//    }

    public void openFile(File f) throws Exception {
        dser = new DeserializedOCAD();
        dser.DeserializeMap(f.getPath());
        ArrayList<IIsoline> isos = dser.toIsolines(1,gf);
        slopeMarks = new ArrayList<>();
        isos.forEach(ic::add);
        dser.slopeMarks.forEach(slopeMarks::add);
    }

    public int IsolineCount() {
        return ic.size();
    }

    public Stream<IIsoline> getIsolinesInCircle(double x, double y, double radius, IsolineContainer ic) {
        Point p = gf.createPoint( new Coordinate(x,y) );
        return ic.stream().filter((il)->
                il.getGeometry().isWithinDistance(p,radius)
        );
    }

    public void connectLines() {
        if (edge == null) return;
        LineWelder lw = new LineWelder(gf,edge);
        //lw.WeldAll(ic);
        ic = new IsolineContainer(gf,lw.WeldAll(ic));
    }

    public void detectEdge() {
        edge = MapEdge.fromIsolines(ic, Constants.EDGE_CONCAVE_THRESHOLD);
    }

    public void buildGraph() {
        NearbyContainer cont = new NearbyContainer(ic);
        NearbyEstimator est = new NearbyEstimator(gf);
        NearbyGraphWrapper graph = new NearbyGraphWrapper(est.getRelationGraph(cont));
        graph.SetHillsSlopeSides();
        graph.ConvertToSpanningTree();
        graph.recoverAllSlopes();
        graph.recoverAllHeights();
        System.out.println("Graph built successfully");
    }

    public void interpolate() {

        Serializer interpolator = new Serializer(ic,1);
        interpolator.writeDataToFile("heights");

    }

    public void generateTexture(String output_path) {
        TextureGenerator gen = new TextureGenerator(dser);
        gen.writeToFile("texture",new PointRasterizer(0.1,ic.getEnvelope()));
    }

}
