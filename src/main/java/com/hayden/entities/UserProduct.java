package com.hayden.entities;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
public class UserProduct {

    @EmbeddedId
    UserProductPK userProductPK;

    @OneToOne
    User user;

    @OneToOne
    Product prodct;

}
