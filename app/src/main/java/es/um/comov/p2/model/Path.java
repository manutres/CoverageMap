package es.um.comov.p2.model;

import java.util.LinkedList;
import java.util.List;

public class Path {

    private List<Sample> samples;
    private int minDistanceBetweenLocations;

    public Path(int minDistanceBetweenLocations) {
        this.samples = new LinkedList<>();
        this.minDistanceBetweenLocations  = minDistanceBetweenLocations;
    }

    public Boolean addSample(Sample sample) {
        if(this.samples.isEmpty()) {
            this.samples.add(sample);
            return true;
        }
        else if ( this.samples.get(this.samples.size()-1).getLocation().distanceTo(sample.getLocation()) > this.minDistanceBetweenLocations) {
            this.samples.add(sample);
            return true;
        }
        return false;
    }

    public Sample getLastSample() {
        if(!this.samples.isEmpty())
            return this.samples.get(this.samples.size()-1);
        return null;
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public void setSamples(List<Sample> samples) {
        this.samples = samples;
    }

    public int getMinDistanceBetweenLocations() {
        return minDistanceBetweenLocations;
    }

    public void setMinDistanceBetweenLocations(int minDistanceBetweenLocations) {
        this.minDistanceBetweenLocations = minDistanceBetweenLocations;
    }
}
