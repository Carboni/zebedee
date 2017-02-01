package com.github.onsdigital.zebedee.content.page.statistics.document.figure.chart;

import java.util.Map;
/**
 * Created by carlhembrough on 10/01/2017.
 */
public class Annotation {

    private int id;
    private int x;
    private int y;
    private String title;
    private String orientation;
    private boolean isHidden;
    private boolean isPlotline;
    private int bandWidth;
    private int width;
    private int height;

    private Map<String, AnnotationDevice> devices;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        isHidden = hidden;
    }

    public boolean isPlotline() {
        return isPlotline;
    }

    public void setPlotline(boolean plotline) {
        isPlotline = plotline;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getBandWidth() {
        return bandWidth;
    }

    public void setBandWidth(int bandWidth) {
        this.bandWidth = bandWidth;
    }
    
    public Map<String, AnnotationDevice> getDevices() {
        return devices;
    }

    public void setDevices(Map<String, AnnotationDevice> devices) {
        this.devices = devices;
    }
}
