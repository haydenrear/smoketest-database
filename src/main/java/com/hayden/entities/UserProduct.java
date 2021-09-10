package com.hayden.entities;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
public class UserProduct {

    @EmbeddedId
    UserProductPK userProductPK;

    public UserProductPK getUserProductPK()
    {
        return userProductPK;
    }

    public void setUserProductPK(UserProductPK userProductPK)
    {
        this.userProductPK = userProductPK;
    }

    @OneToOne
    User user;

    @OneToOne
    Product prodct;

}
