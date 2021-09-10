package com.hayden;

import com.hayden.entities.User;
import com.hayden.entities.UserProductPK;
import groovy.lang.Tuple;
import groovy.lang.Tuple2;
import io.quarkus.test.junit.QuarkusTest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.*;
import javax.persistence.metamodel.EntityType;
import javax.transaction.Transactional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class SmokeTestDatabase {

    @Inject
    ProductService productService;
    @Inject
    EntityManager entityManager;

    @Test
    public void testPersistUser()
    {
        User hayden = productService.addUser("hayden");
        User user = entityManager.find(User.class, hayden.getUserId());
        assertTrue(user.getUserId() != 0);
    }


    @Test
    @Transactional
    public void testCreate()
    {
        List<Object> collect = createBase(entityManager.getMetamodel().getEntities());
        List<? extends EntityVal<?>> collect1 = collect.stream().map(obj -> {
                    var entityVal = new EntityVal<>(obj.getClass(), obj);
                    entityVal.obj = obj;
                    entityVal.setRelationship(entityManager.getMetamodel().getEntities(), collect);
                    return entityVal;
                })
                .collect(Collectors.toList());
        collect1.stream().forEach(ev -> {
            try {
                ev.instantiate(entityManager.getMetamodel().getEntities(), entityManager);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        });

        collect1.forEach(entityVal -> {
            System.out.println(entityVal.obj);
            System.out.println(entityVal.obj.getClass().getName());
            Arrays.stream(entityVal.obj.getClass().getDeclaredFields()).filter(EntityVal::notTransient)
                    .forEach(f -> {
                        try {
                            f.trySetAccessible();
                            System.out.println(f.getName()+": "+f.get(entityVal.obj));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
        });
        System.out.println(EntityVal.relationships);

        collect.forEach(entityManager::persist);
        Optional<Object> any = collect.stream().filter(obj -> obj instanceof UserProductPK).findAny();
        assertTrue(any.isPresent());
        UserProductPK o = (UserProductPK) any.get();
        assertTrue(o.getProductId() != 0);
        assertTrue(o.getUserId() != 0);

    }

    @AllArgsConstructor
    @Data
    public static class EntityVals {
        EntityVal<?> entityVal2;
        EntityVal<?> entityVal1;
    }

    @AllArgsConstructor
    @Data
    public static class EntityVal<T> {

        static List<Relationship> relationships = new ArrayList<>();
        Class<T> clzzType;
        Object obj;

        public Optional<Object> findByType(Class<?> obj){
            return relationships.stream()
                    .flatMap(relationship -> {
                        if(relationship.entityVals.entityVal1.clzzType == obj)
                            return Stream.of(relationship.entityVals.entityVal1.obj);
                        else if(relationship.entityVals.entityVal2.clzzType == obj)
                            return Stream.of(relationship.entityVals.entityVal2.obj);
                        return Stream.empty();
                    })
                    .findFirst();
        }

        public void instantiate(Set<EntityType<?>> entities, EntityManager entityManager) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException
        {
            relationships.stream().filter(relationship -> relationship.entityVals.entityVal1 == this || relationship.entityVals.entityVal2 == this)
                    .forEach(Relationship::setVals);
            for (Relationship relationship : relationships) {
                setComposite(entities, relationship, entityManager);
            }
        }

        private void setComposite(
                Set<EntityType<?>> entities, Relationship relationship, EntityManager entityManager
        )
        {
            setCompositeKeyIfPresent(entities, entityManager, relationship.entityVals.entityVal1);
            setCompositeKeyIfPresent(entities, entityManager, relationship.entityVals.entityVal2);
        }

        private void setCompositeKeyIfPresent(
                Set<EntityType<?>> entities, EntityManager entityManager, EntityVal<?> entityVal2
        )
        {
            entityVal2.isCompositeKey().ifPresent(compositeKeyVal -> {
                try {
                    setCompositeKeyForEntity(entities, compositeKeyVal, entityManager);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            });
        }

        private void setCompositeKeyForEntity(
                Set<EntityType<?>> entities, Field compositeField, EntityManager entityManager
        ) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
        {
                Object compositeKeyCreated = createCompositeKey(entities, entityManager, compositeField);
                entities.forEach(entity -> findObjectWithFieldLike(compositeKeyCreated.getClass(), entity)
                        .forEach(f -> {
                            try {
                                f.trySetAccessible();
                                f.set(findByType(entity.getJavaType()).get(), compositeKeyCreated);
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        }));
        }

        private Stream<Field> findObjectWithFieldLike(Class<?> compositeKeyCreated, EntityType<?> entity)
        {
            return Arrays.stream(entity.getJavaType().getDeclaredFields())
                    .filter(f -> f.getType() == compositeKeyCreated);
        }

        private Object createCompositeKey(
                Set<EntityType<?>> entities, EntityManager entityManager, Field compositeField
        ) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException
        {
            Object compositeKeyCreated = compositeField.getType().getConstructor(null).newInstance(null);
            Arrays.stream(compositeField.getType().getDeclaredFields())
                    .forEach(f -> entities.stream()
                            .filter(entity -> f.getName().toLowerCase().contains(entity.getJavaType().getSimpleName().toLowerCase()))
                            .forEach(entityType -> Arrays.stream(entityType.getJavaType().getDeclaredFields())
                                    .filter(entityField -> Arrays.stream(entityField.getAnnotations()).anyMatch(a -> a.annotationType().equals(Id.class)))
                                    .forEach(field -> {
                                        field.trySetAccessible();
                                        try {
                                            Object obj = findByType(entityType.getJavaType()).get();
                                            entityManager.persist(obj);
                                            Object o = field.get(obj);
                                            f.trySetAccessible();
                                            f.set(compositeKeyCreated, o);
                                        } catch (IllegalAccessException e) {
                                            e.printStackTrace();
                                        }
                                    }))
                    );
            return compositeKeyCreated;
        }

        private Optional<Field> isCompositeKey()
        {
            return Arrays.stream(obj.getClass().getDeclaredFields())
                    .filter(f -> Arrays.stream(f.getAnnotations())
                            .anyMatch(annotation -> annotation.annotationType().equals(EmbeddedId.class) || annotation.annotationType().equals(IdClass.class))
                    )
                    .findAny();
        }

        private boolean isRequired(Field f)
        {
            return Arrays.stream(f.getAnnotations())
                    .noneMatch(a -> a.annotationType().equals(NotNull.class) || a.annotationType().equals(NotEmpty.class));
        }

        public void setRelationship(Set<EntityType<?>> entities, List<Object> objects)
        {
            Arrays.stream(obj.getClass().getDeclaredFields())
                    .filter(this::isRequired)
                    .filter(this::notId)
                    .filter(EntityVal::notTransient)
                    .flatMap(f -> setPrimitives(f, obj))
                    .flatMap(f -> {
                        Class<?> fieldOrGenericType = getFieldOrGenericType(f);
                        return findAndCreateIfNecessary(entities, f, fieldOrGenericType);
                    })
                    .forEach(f -> {
                        Class<?> fieldOrGenericType = getFieldOrGenericType(f.getV1());
                        if(!doesRelationshipExist(f)){
                                Object o =  objects.stream().filter(obj -> obj.getClass().equals(fieldOrGenericType)).findFirst().get();
                                EntityVal entityVal = new EntityVal(fieldOrGenericType, o);
                                entityVal.obj = o;
                                entityVal.clzzType = fieldOrGenericType;
                                EntityVals entityVals = new EntityVals(entityVal, this);
                                Relationship relationship = new Relationship(entityVals, objects);
                                relationships.add(relationship);
                        }
                    });
        }

        public static boolean notTransient(Field field)
        {
            return Arrays.stream(field.getAnnotations())
                    .noneMatch(a -> a.annotationType().equals(Transient.class));
        }

        private boolean notId(Field field)
        {
            return Arrays.stream(field.getAnnotations())
                    .noneMatch(a -> a.annotationType().equals(Id.class) || a.annotationType().equals(EmbeddedId.class));
        }

        private boolean doesRelationshipExist(Tuple2<Field, ? extends EntityType<?>> f)
        {
            Predicate<Relationship> findEntity = (Relationship entry) ->
                        (entry.entityVals.entityVal1.clzzType == clzzType && entry.entityVals.entityVal2.clzzType == f.getV1().getType())
                        ||
                        (entry.entityVals.entityVal2.clzzType == clzzType && entry.entityVals.entityVal1.clzzType == f.getV1().getType());

            Optional<Relationship> first = relationships.stream()
                    .filter(findEntity)
                    .findFirst();
            return first.isPresent();
        }

        private Stream<Tuple2<Field, ? extends EntityType<?>>> findAndCreateIfNecessary(
                Set<EntityType<?>> entities, Field f, Class<?> fieldOrGenericType
        )
        {
            if(entities.stream().anyMatch(meta -> Objects.equals(fieldOrGenericType, meta.getJavaType()))){
                var first =  entities.stream()
                        .filter(entityType -> entityType.getJavaType().equals(fieldOrGenericType))
                        .map(val -> Tuple.tuple(f, val))
                        .findFirst();
                return Stream.ofNullable(first.orElse(null));
            }
            else {
                try {
                    if(Collection.class.isAssignableFrom(f.getType())){
                        f.set(obj, new ArrayList<>());
                    }
                    else if(Arrays.stream(f.getAnnotations()).anyMatch(n -> n.annotationType().equals(NotNull.class))) {
                        Object val = f.getClass().getConstructor(null).newInstance(null);
                        f.trySetAccessible();
                        f.set(obj, val);
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
                return Stream.of();
            }
        }

        @SneakyThrows
        private Stream<Field> setPrimitives(Field field, Object object)
        {
            field.trySetAccessible();
            if(field.getType().isAssignableFrom(String.class)){
                field.set(object, "default");
                return Stream.of();
            }
            else if(ClassUtils.isPrimitiveOrWrapper(field.getType())){
                Class<?> primitiveWrapperType = ClassUtils.primitiveToWrapper(field.getType());
                Integer i = 1;
                try {
                    Object cast = primitiveWrapperType.cast(1);
                    field.set(object, cast);
                } catch (ClassCastException e){

                }
                return Stream.of();
            }
            return Stream.of(field);
        }

    }

    @Data
    public static class Relationship {

        private final List<Object> objects;
        boolean isMany;
        boolean required;
        boolean isComposite;
        boolean hasBeenSet = false;

        EntityVal<?> isParent;
        Object compositeKey;
        EntityVals entityVals;

        public Relationship(EntityVals entityVals, List<Object> objects)
        {
            this.entityVals = entityVals;
            this.objects = objects;
        }

        public void setVals()
        {
            setValsForEntityVals(entityVals.entityVal1, entityVals.entityVal2);
            setValsForEntityVals(entityVals.entityVal2, entityVals.entityVal1);
            isParent = getParent(entityVals.entityVal1, entityVals.entityVal2).orElse(null);
        }

        private Optional<EntityVal<?>> getParent(EntityVal<?> entityVal1, EntityVal<?> entityVal2)
        {
            Optional<Field> val1Field = getField(entityVal1);
            Optional<Field> val2Field = getField(entityVal2);
            if(val1Field.isEmpty())
                return Optional.of(entityVal2);
            else if(val2Field.isEmpty())
                 return Optional.of(entityVal1);
            if(isMappedBy(entityVal1))
                return Optional.of(entityVal2);
            else if(isMappedBy(entityVal2))
                return Optional.of(entityVal1);
            return Optional.empty();
        }

        private boolean isMappedBy(EntityVal<?> val1)
        {
            return getField(val1).map(f -> Arrays.stream(f.getAnnotations()).anyMatch(this::checkMappedBy)).isPresent();
        }

        private boolean checkMappedBy(Annotation annotation){
            try {
                Method mappedBy = annotation.annotationType().getMethod("mappedBy");
                String str = (String) mappedBy.invoke(annotation, null);
                return str != null && !str.equals("");
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
        }

        private void setValsForEntityVals(EntityVal<?> ev1, EntityVal<?> ev2)
        {
            if(isCollection(ev1) && isCollection(ev2)){
                instantiateCollection(ev1);
                instantiateCollection(ev1);
            }
            else if(isCollection(ev1)) {
                instantiateCollection(ev1);
            }
            else if(isCollection(ev2)){
                instantiateCollection(ev2);
            }
            else {
                getField(ev1).ifPresent(f -> {
                    try {
                        f.trySetAccessible();
                        f.set(ev2.obj, ev1.obj);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        @SneakyThrows
        private <T> Optional<Collection<T>> instantiateCollection(EntityVal<T> ev1)
        {
            Optional<Field> fieldAssociatedWith = getFieldAssociatedWith(ev1, getOther(ev1));
            if(fieldAssociatedWith.isPresent()) {
                if(Collection.class.isAssignableFrom(fieldAssociatedWith.get().getType())){
                    ArrayList<Object> objects = new ArrayList<>();
                    objects.add(ev1.obj);
                    fieldAssociatedWith.get().trySetAccessible();
                    fieldAssociatedWith.get().set(getOther(ev1).obj, objects);
                }
            }
            return Optional.empty();
        }


        private EntityVal<?> getOther(EntityVal<?> ev){
            return ev == entityVals.entityVal1 ? entityVals.entityVal2 : entityVals.entityVal1;
        }

        private boolean isCollection(EntityVal ev1)
        {
            var type = getField(ev1);
            if(type.isEmpty())
                return false;
            return Collection.class.isAssignableFrom(type.get().getType());
        }

        public Optional<Field> getField(EntityVal entityVal){
            if(entityVal == entityVals.entityVal1) {
                return getFieldAssociatedWith(entityVal, entityVals.entityVal2);
            }
            return getFieldAssociatedWith(entityVal, entityVals.entityVal1);
        }

        public Optional<Field> getFieldAssociatedWith(EntityVal entityVal, EntityVal toSearch){
            return Arrays.stream(toSearch.obj.getClass().getDeclaredFields())
                    .filter(EntityVal::notTransient)
                    .filter(f -> getFieldOrGenericType(f).equals(entityVal.clzzType))
                    .findFirst();
        }



    }

    @SneakyThrows
    private String toMatchWith()
    {
        return "somestring";
    }

    private List<Object> createBase(Set<EntityType<?>> entity)
    {
        return entity.stream().flatMap(entityFound -> {
            try {
                return Stream.of(entityFound.getJavaType()
                        .getConstructor(null)
                        .newInstance(null));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
            return Stream.empty();
        }).collect(Collectors.toList());
    }

    @SneakyThrows
    public static Class<?> getFieldOrGenericType(Field f)
    {
        if(Collection.class.isAssignableFrom(f.getType())){
            Type genericType = f.getGenericType();
            if(genericType instanceof ParameterizedType pt){
                return Class.forName(pt.getActualTypeArguments()[0].getTypeName());
            }
            else return Class.forName(genericType.getTypeName());
        }
        else return f.getType();
    }


    // need to recursively create objects by going through each field of object and
    //  1. determine if necessary
    //  2. create object
    //  3. if bidirectional, add to list of objects for the parent object
    //  4. determine where in the hierarchy of persisting it is (so no constrain violations)

}
