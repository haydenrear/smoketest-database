package com.hayden.entities;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name= "product_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column
    String username;

    @ManyToMany
    List<Product> productList;



    public User()
    {
    }

    public User(String username)
    {
        this.username = username;
    }

    public void setProductList(List<Product> productList)
    {
        this.productList = productList;
    }

    public List<Product> getProductList()
    {
        return productList;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }
}
