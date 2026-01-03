package com.example.studysync;

// app/src/main/java/com/example/studysync/User.java
public class User {
    public String name;
    public String email;
    public String photoUrl;

    // Required empty public constructor for Firebase
    public User() {
    }

    public User(String name, String email, String photoUrl) {
        this.name = name;
        this.email = email;
        this.photoUrl = photoUrl;
    }
}