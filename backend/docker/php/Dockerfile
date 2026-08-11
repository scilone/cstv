FROM composer:2.8 AS composer

FROM php:8.5-fpm-alpine

RUN apk add --no-cache libcurl libpq oniguruma \
    && apk add --no-cache --virtual .build-deps $PHPIZE_DEPS curl-dev oniguruma-dev postgresql-dev \
    && docker-php-ext-install -j"$(nproc)" curl mbstring pdo_pgsql \
    && apk del .build-deps \
    && rm -rf /tmp/*

COPY --from=composer /usr/bin/composer /usr/local/bin/composer
COPY docker/php/php.ini /usr/local/etc/php/conf.d/99-cstv.ini
COPY docker/php/www.conf /usr/local/etc/php-fpm.d/zz-cstv.conf

WORKDIR /var/www/html

COPY composer.json composer.lock* ./
RUN composer install --no-interaction --no-progress --prefer-dist --optimize-autoloader

COPY . .

RUN chmod +x bin/migrate bin/fixtures \
    && chown -R www-data:www-data /var/www/html

USER www-data

CMD ["php-fpm", "-F"]
