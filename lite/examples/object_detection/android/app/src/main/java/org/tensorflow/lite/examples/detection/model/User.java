package org.tensorflow.lite.examples.detection.model;

public class User {
    private String id;
    private String username;

    public User (String id, String username){
        this.id = id;
        this.username = username;
    }

    public User(){

    }


    public void setId(String id){
        this.id = id;
    }

    public void setUsername(String username){
        this.username = username;
    }

    public String getId(){
        return this.id;
    }

    public String getUsername(){
        return this.username;
    }

}
