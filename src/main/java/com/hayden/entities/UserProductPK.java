package com.hayden.entities;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class UserProductPK implements Serializable {
    long userId;
    long productId;

    public long getUserId()
    {
        return userId;
    }

    public long getProductId()
    {
        return productId;
    }

    public void setUserId(long userId)
    {
        this.userId = userId;
    }

    public void setProductId(long productId)
    {
        this.productId = productId;
    }
}
