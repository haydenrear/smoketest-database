package com.hayden;

import com.hayden.entities.User;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@ApplicationScoped
public class ProductService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public User addUser(String username)
    {
        User user = new User(username);
        entityManager.persist(user);
        return user;
    }

}
