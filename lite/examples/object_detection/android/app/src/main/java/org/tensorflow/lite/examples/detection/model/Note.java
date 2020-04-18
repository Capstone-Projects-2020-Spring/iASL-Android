package org.tensorflow.lite.examples.detection.model;

/**
 * This class represents a note object, which has an id, uid of its owner, content, a timestamp, and a title
 */
public class Note {

    private String id;
    private String ownerId;
    private String text;
    private long timestamp;
    private String title;

    public Note (String id, String ownerId, String text, long timestamp, String title){
        this.id = id;
        this.ownerId = ownerId;
        this.text = text;
        this.timestamp = timestamp;
        this.title = title;
    }

    public Note(){

    }


    public void setId(String id){
        this.id = id;
    }

    public void setOwnerId(String ownerId){
        this.ownerId = ownerId;
    }

    public void setText(String text) {this.text = text;}

    public void setTimestamp(long timestamp){this.timestamp = timestamp;}

    public void setTitle(String title){this.title = title;}

    public String getId(){
        return this.id;
    }

    public String getOwnerId(){
        return this.ownerId;
    }

    public String getText(){return this.text;}

    public long getTimestamp(){return this.timestamp;}

    public String getTitle(){return this.title;}
}
