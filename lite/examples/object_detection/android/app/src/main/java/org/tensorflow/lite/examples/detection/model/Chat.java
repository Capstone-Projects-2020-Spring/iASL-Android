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

    public Chat(){

    }

    public String getSenderId(){
        return senderId;
    }

    public void setSender(String senderId){
        this.senderId = senderId;
    }

    public String getReceiverId(){
        return receiverId;
    }

    public void setReceiverId(String receiverId){
        this.receiverId = receiverId;
    }

    public String getText(){
        return text;
    }

    public void setText(String text){
        this.text = text;
    }

    public long getTimestamp(){
        return timestamp;
    }

    public void setTimestamp(long timestamp){
        this.timestamp = timestamp;
    }



}
