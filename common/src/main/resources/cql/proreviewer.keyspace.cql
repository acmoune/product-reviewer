CREATE KEYSPACE proreviewer WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};

create table proreviewer.reviews_by_product (
    product_id text,
    status text,
    created_at bigint,
    review_id text,

    product_name text,
    rating int,
    content text,
    user_id text,
    username text,
    user_email text,
    PRIMARY KEY ((product_id), status, created_at, review_id)
) WITH clustering order by (status ASC, created_at DESC, review_id ASC);

create materialized view proreviewer.reviews AS
    SELECT
        review_id,
        product_id,
        status,
        created_at,

        product_name,
        rating,
        content,
        user_id,
        username,
        user_email
    FROM proreviewer.reviews_by_product
    WHERE
        review_id IS NOT NULL
        AND product_id IS NOT NULL
        AND created_at IS NOT NULL
        AND status IS NOT NULL
        AND rating IS NOT NULL
    PRIMARY KEY ((review_id), status, created_at, product_id);

create table proreviewer.stats (
    product_id text,
    product_title text,
    count_one int,
    count_two int,
    count_three int,
    count_four int,
    count_five int,
    total_reviews bigint,
    total_rating bigint,
    PRIMARY KEY (product_id)
);

create table proreviewer.tasks (
    task_id text,
    resource_id text,
    status text,
    error_message text,
    created_at bigint,
    updated_at bigint,
    PRIMARY KEY (task_id)
);