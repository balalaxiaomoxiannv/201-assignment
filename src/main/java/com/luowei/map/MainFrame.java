package com.luowei.map;

import com.alibaba.fastjson.JSONObject;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.simple.SimpleFeature;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends Application {

    private List<SimpleFeature> countryFeatureList = new ArrayList<>();

    /**
     * 读取国家的边界数据
     */
    private void readAllCountryBorder() throws Exception{
        ShapefileDataStore continentShpStore =  new ShapefileDataStore(ClassLoader.getSystemClassLoader().getResource("shp/world_borders.shp").toURI().toURL());
        Charset charset = Charset.forName("GBK");
        continentShpStore.setCharset(charset);
        SimpleFeatureSource sfSource = continentShpStore.getFeatureSource();
        SimpleFeatureIterator sfIter = sfSource.getFeatures().features();
        while (sfIter.hasNext()) {
            SimpleFeature feature = (SimpleFeature) sfIter.next();
            countryFeatureList.add(feature);
        }
        sfIter.close();
        continentShpStore.dispose();
    }

    /**
     * 读取七大洲的边界数据，并传递给百度地图进行绘制
     */
    private void drawContinentBorder(WebEngine webEngine) throws Exception {
        ShapefileDataStore continentShpStore =  new ShapefileDataStore(ClassLoader.getSystemClassLoader().getResource("shp/continent.shp").toURI().toURL());
        Charset charset = Charset.forName("GBK");
        continentShpStore.setCharset(charset);
        SimpleFeatureSource sfSource = continentShpStore.getFeatureSource();
        SimpleFeatureIterator sfIter = sfSource.getFeatures().features();
        // 从ShapeFile文件中遍历每一个Feature，然后将Feature转为GeoJSON字符串
        while (sfIter.hasNext()) {
            SimpleFeature feature = (SimpleFeature) sfIter.next();
            if(feature.getDefaultGeometry() instanceof MultiPolygon){
                MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometry();
                for (Integer i=0;i<mp.getNumGeometries();i++){
                    Polygon polygon = (Polygon) mp.getGeometryN(i);
                    StringBuilder sb = new StringBuilder("[");
                    for (Coordinate coordinate : polygon.getCoordinates()){
                        sb.append("{\"x\":").append(coordinate.x).append(",\"y\":").append(coordinate.y).append("},");
                    }
                    if(sb.toString().endsWith(",")){
                        sb.deleteCharAt(sb.length()-1);
                    }
                    sb.append("]");
                    webEngine.executeScript("drawContinent("+sb.toString()+")");
                }
            }else{
                System.out.println(feature.getDefaultGeometry());
            }
        }
        sfIter.close();
        continentShpStore.dispose();
    }

    /**
     * 读取国家的边界数据，并传递给百度地图进行绘制
     * @param webEngine
     * @param lng
     * @param lat
     * @throws Exception
     */
    private void drawCountryBorder(WebEngine webEngine,Double lng,Double lat) throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);
        WKTReader wktReader = new WKTReader(geometryFactory);
        Geometry geometry = wktReader.read("POINT ("+lng+" "+lat+")");
        // 从ShapeFile文件中遍历每一个Feature，然后将Feature转为GeoJSON字符串
        for (SimpleFeature feature:countryFeatureList) {
            if(feature.getDefaultGeometry() instanceof  MultiPolygon){
                MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometry();
                if(mp.contains(geometry)){
                    for (Integer i=0;i<mp.getNumGeometries();i++){
                        Polygon polygon = (Polygon) mp.getGeometryN(i);
                        StringBuilder sb = new StringBuilder("[");
                        for (Coordinate coordinate : polygon.getCoordinates()){
                            sb.append("{\"x\":").append(coordinate.x).append(",\"y\":").append(coordinate.y).append("},");
                        }
                        if(sb.toString().endsWith(",")){
                            sb.deleteCharAt(sb.length()-1);
                        }
                        sb.append("]");
                        webEngine.executeScript("drawCountry("+sb.toString()+")");
                    }
                }
            }
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        //设置窗体布局
        BorderPane borderPane = new BorderPane();
        //创建scene
        Scene scene = new Scene(borderPane);

        //创建WebView和WebEngine对象
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
            @Override
            public void handle(WebEvent<String> event) {
                if(event.getData()!=null&&!event.getData().trim().equals("")){
                    JSONObject jsonObject = JSONObject.parseObject(event.getData());
                    String type = jsonObject.getString("type");
                    if(type.equals("continentsClick")){
                        JSONObject latlng = jsonObject.getJSONObject("data");
                        Double lat = latlng.getDouble("lat");
                        Double lng = latlng.getDouble("lng");
                        try {
                            drawCountryBorder(webEngine,lng,lat);
                        }catch (Exception ex){

                        }
                    }else if(type.equals("countryClick")){
                        try {
                            System.out.println(event.getData());
                            JSONObject latlng = jsonObject.getJSONObject("data");
                            Double lat = latlng.getDouble("lat");
                            Double lng = latlng.getDouble("lng");
                            GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);
                            WKTReader wktReader = new WKTReader(geometryFactory);
                            Geometry geometry = wktReader.read("POINT (" + lng + " " + lat + ")");
                            for (SimpleFeature feature : countryFeatureList) {
                                if (feature.getDefaultGeometry() instanceof MultiPolygon) {
                                    MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometry();
                                    if (mp.contains(geometry)) {
                                        String climate = feature.getAttribute("climate").toString();
                                        String environmen = feature.getAttribute("environmen").toString();
                                        String population = feature.getAttribute("population").toString();
                                        String cover = feature.getAttribute("cover").toString();
                                        String name = feature.getAttribute("name").toString();
                                        String testfield = feature.getAttribute("testfield").toString();
                                        System.out.println(climate);
                                        System.out.println(environmen);
                                        System.out.println(population);
                                        System.out.println(cover);
                                        StringBuilder sb = new StringBuilder("{");
                                        sb.append("\"climate\":\"").append(climate).append("\"");
                                        sb.append(",\"environmen\":\"").append(environmen).append("\"");
                                        sb.append(",\"population\":\"").append(population).append("\"");
                                        sb.append(",\"lng\":").append(lng).append(",\"lat\":").append(lat);
                                        sb.append(",\"cover\":\"").append(cover).append("\"");
                                        sb.append(",\"name\":\"").append(name).append("\"");
                                        sb.append(",\"testfield\":\"").append(testfield).append("\"");
                                        sb.append("}");
                                        webEngine.executeScript("showCountryData("+sb.toString()+")");
                                    }
                                }
                            }
                        }catch (ParseException ex){

                        }
                    }
                }
            }
        });
        webEngine.setJavaScriptEnabled(true);
        //使用类加载器加载本地HTML代码
        webEngine.load(ClassLoader.getSystemClassLoader().getResource("html/map.html").toExternalForm());
        webEngine.documentProperty().addListener((ov, oldDoc, doc) -> {
            if (doc != null ) {
                webEngine.executeScript("initMap()");
                try {
                    //读取全球国家边界数据
                    readAllCountryBorder();
                    //绘制全球七大洲边界数据
                    drawContinentBorder(webEngine);
                }catch (Exception ex){

                }
            }
        });
        borderPane.setCenter(webView);

        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
