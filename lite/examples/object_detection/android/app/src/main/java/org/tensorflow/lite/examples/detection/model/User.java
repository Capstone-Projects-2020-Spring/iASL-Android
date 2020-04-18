package org.tensorflow.lite.examples.detection.model;

/**
 * This class represents a user instance, which has an uid, an username, and an email.
 */
public class User {
    private String id;
    private String name;
    private String email;

    public User (String email, String id, String name){
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public User(){

    }


    public void setId(String id){
        this.id = id;
    }

    public void setName(String name){
        this.name = name;
    }

    public void setEmail(String email) {this.email = email;}

    public String getId(){
        return this.id;
    }

    public String getName(){
        return this.name;
    }

    public String getEmail(){return this.email;}



}
