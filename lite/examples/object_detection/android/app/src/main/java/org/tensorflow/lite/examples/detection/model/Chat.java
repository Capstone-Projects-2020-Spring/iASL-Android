package org.tensorflow.lite.examples.detection.model;

public class Chat {
    private String senderId;
    private String receiverId;
    private String text;
    private long timestamp;

    public Chat(String receiverId, String senderId, String text, long timestamp){
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = timestamp;
    }

    ///Default constructor
    public Chat(){

    }

    ///Returns the sender ID as a string
    public String getSenderId(){
        return senderId;
    }

    public void setSender(String senderId){
        this.senderId = senderId;
    }

    ///Returns the receiver ID as a String
    public String getReceiverId(){
        return receiverId;
    }

    public void setReceiverId(String receiverId){
        this.receiverId = receiverId;
    }

    ///Returns the text as a String
    public String getText(){
        return text;
    }

    public void setText(String text){
        this.text = text;
    }

    ///Returns the timestamp as a long
    public long getTimestamp(){
        return timestamp;
    }

    public void setTimestamp(long timestamp){
        this.timestamp = timestamp;
    }



}
