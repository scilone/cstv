.PHONY: up down migrate fixtures test test-unit test-integration test-functional test-prepare logs

up:
	docker compose up -d --build

down:
	docker compose down

migrate:
	docker compose exec php bin/migrate

fixtures:
	docker compose exec php bin/fixtures

test:
	docker compose up -d --build postgres-test php-test nginx-test
	docker compose exec -T php-test composer test

test-prepare:
	docker compose up -d --build postgres-test php-test nginx-test
	docker compose exec -T php-test composer test:prepare

test-unit: test-prepare
	docker compose exec -T php-test vendor/bin/phpunit --testsuite Unit

test-integration: test-prepare
	docker compose exec -T php-test vendor/bin/phpunit --testsuite Integration

test-functional: test-prepare
	docker compose exec -T php-test vendor/bin/phpunit --testsuite Functional

logs:
	docker compose logs -f
