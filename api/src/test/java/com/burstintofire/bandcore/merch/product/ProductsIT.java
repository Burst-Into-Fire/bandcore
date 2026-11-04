package com.burstintofire.bandcore.merch.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.burstintofire.bandcore.support.ApiClient;
import com.burstintofire.bandcore.support.IntegrationTest;
import com.burstintofire.bandcore.support.OwnerSql;

class ProductsIT extends IntegrationTest {

	private static final String SHIRT = """
			{"name": "Camiseta preta", "description": "Algodão", "currency": "BRL", "defaultPriceCents": 6000,
			 "variants": [{"size": "M", "sku": "CAM-M"}, {"size": "G", "lowStockThreshold": 5},
			              {"size": "GG", "priceCents": 6500}]}""";

	UUID alpha;

	ApiClient band;

	String admin;

	String member;

	@BeforeEach
	void bandWithAnAdminAndAMember() {
		alpha = fixtures.band("alpha");
		fixtures.membership(alpha, fixtures.verifiedUser("ana@example.com"), "ADMIN");
		fixtures.membership(alpha, fixtures.verifiedUser("bia@example.com"), "MEMBER");
		band = api.forBand("alpha");
		admin = fixtures.login(band, "ana@example.com");
		member = fixtures.login(band, "bia@example.com");
	}

	@Test
	void theCatalogFromTheMockupCanBeRegistered() {
		ResponseEntity<String> shirt = create(SHIRT);
		ResponseEntity<String> patch = create("""
				{"name": "Patch", "currency": "BRL", "defaultPriceCents": 1500}""");

		assertThat(shirt.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String shirtId = JsonPath.read(shirt.getBody(), "$.id");
		assertThat(shirt.getHeaders().getLocation()).hasToString("/api/products/" + shirtId);
		assertThat(JsonPath.<List<String>>read(shirt.getBody(), "$.variants[*].size")).containsExactly("M", "G", "GG");
		assertThat(JsonPath.<List<Integer>>read(shirt.getBody(), "$.variants[*].effectivePriceCents"))
			.containsExactly(6000, 6000, 6500);
		assertThat(JsonPath.<List<Integer>>read(shirt.getBody(), "$.variants[*].lowStockThreshold"))
			.containsExactly(0, 5, 0);
		// Without variants, the patch gets one default variant: no size, no color.
		assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(JsonPath.<List<Object>>read(patch.getBody(), "$.variants[*]")).hasSize(1);
		assertThat(JsonPath.<Object>read(patch.getBody(), "$.variants[0].size")).isNull();

		// Everyone reads the catalog, by name.
		String catalog = band.get("/api/products", headers -> headers.setBearerAuth(member)).getBody();
		assertThat(JsonPath.<List<String>>read(catalog, "$[*].name")).containsExactly("Camiseta preta", "Patch");
		assertThat(JsonPath.<String>read(catalog, "$[0].currency")).isEqualTo("BRL");
		assertThat(JsonPath.<String>read(catalog, "$[0].description")).isEqualTo("Algodão");
		assertThat(auditActions("product.%")).containsExactly("product.created", "product.created");
		assertThat(auditActions("product_variant.%")).hasSize(4).containsOnly("product_variant.created");
	}

	@Test
	void skusAreUniquePerBandButReusableInAnotherBand() {
		create(SHIRT);

		ResponseEntity<String> taken = create("""
				{"name": "Moletom", "currency": "BRL", "defaultPriceCents": 12000,
				 "variants": [{"size": "M"}, {"size": "G", "sku": " cam-m "}]}""");
		assertProblem(taken, HttpStatus.CONFLICT, "SKU_TAKEN");
		assertThat(JsonPath.<String>read(taken.getBody(), "$.errors[0].field")).isEqualTo("variants[1].sku");

		UUID bravo = fixtures.band("bravo");
		fixtures.membership(bravo, fixtures.verifiedUser("caio@example.com"), "ADMIN");
		ApiClient other = api.forBand("bravo");
		String caio = fixtures.login(other, "caio@example.com");
		assertThat(other.postJson("/api/products", SHIRT, headers -> headers.setBearerAuth(caio)).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.products")).isEqualTo("2");
	}

	@Test
	void invalidProductsAreRefusedAndNothingIsSaved() {
		assertProblem(create("""
				{"name": "Boné", "currency": "BRL", "defaultPriceCents": -1}"""), HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED");
		assertProblem(create("""
				{"name": "Boné", "currency": "BRL", "defaultPriceCents": 100, "variants": [{"priceCents": -5}]}"""),
				HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(create("""
				{"name": "Boné", "defaultPriceCents": 100}"""), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
		assertProblem(create("""
				{"name": "Boné", "currency": "EUR", "defaultPriceCents": 100}"""), HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST");
		ResponseEntity<String> repeated = create("""
				{"name": "Boné", "currency": "BRL", "defaultPriceCents": 100,
				 "variants": [{"color": "Preto"}, {"color": " preto "}]}""");
		assertProblem(repeated, HttpStatus.CONFLICT, "VARIANT_TAKEN");
		assertThat(JsonPath.<String>read(repeated.getBody(), "$.errors[0].field")).isEqualTo("variants[1].size");
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.products")).isEqualTo("0");

		create(SHIRT);
		assertProblem(create("""
				{"name": "CAMISETA PRETA", "currency": "USD", "defaultPriceCents": 100}"""), HttpStatus.CONFLICT,
				"PRODUCT_NAME_TAKEN");
	}

	@Test
	void productChangesNeedTheVersionThatWasReadAndAreAudited() {
		String id = JsonPath.read(create(SHIRT).getBody(), "$.id");

		ResponseEntity<String> changed = band.patchJson("/api/products/" + id, """
				{"version": 0, "name": "Camiseta preta logo", "description": "", "defaultPriceCents": 7000}""",
				headers -> headers.setBearerAuth(admin));

		assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<String>read(changed.getBody(), "$.name")).isEqualTo("Camiseta preta logo");
		assertThat(JsonPath.<Object>read(changed.getBody(), "$.description")).isNull();
		assertThat(JsonPath.<Integer>read(changed.getBody(), "$.version")).isEqualTo(1);
		// Variants without their own price follow the product's.
		assertThat(JsonPath.<List<Integer>>read(changed.getBody(), "$.variants[*].effectivePriceCents"))
			.containsExactly(7000, 7000, 6500);
		assertThat(OwnerSql.superuserQueryForString(
				"SELECT changes::text FROM bandcore.audit_events WHERE action = 'product.updated'"))
			.contains("\"default_price_cents\": {\"to\": 7000, \"from\": 6000}");

		ResponseEntity<String> stale = band.patchJson("/api/products/" + id, """
				{"version": 0, "name": "Outra"}""", headers -> headers.setBearerAuth(admin));
		assertProblem(stale, HttpStatus.CONFLICT, "VERSION_CONFLICT");
	}

	@Test
	void variantsAreAddedAndReplacedWhole() {
		String shirt = create(SHIRT).getBody();
		String productId = JsonPath.read(shirt, "$.id");
		String gg = JsonPath.read(shirt, "$.variants[2].id");

		ResponseEntity<String> added = band.postJson("/api/products/" + productId + "/variants", """
				{"size": "XG", "color": "Preto"}""", headers -> headers.setBearerAuth(admin));
		assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertProblem(band.postJson("/api/products/" + productId + "/variants", """
				{"size": "xg", "color": "PRETO"}""", headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT,
				"VARIANT_TAKEN");

		// Without a price, GG sells at the product's price again.
		ResponseEntity<String> replaced = band.send(HttpMethod.PUT, "/api/variants/" + gg, """
				{"version": 0, "size": "GG", "sku": "CAM-GG", "lowStockThreshold": 3}""",
				headers -> headers.setBearerAuth(admin));
		assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(JsonPath.<Object>read(replaced.getBody(), "$.priceCents")).isNull();
		assertThat(JsonPath.<Integer>read(replaced.getBody(), "$.effectivePriceCents")).isEqualTo(6000);
		assertThat(JsonPath.<String>read(replaced.getBody(), "$.sku")).isEqualTo("CAM-GG");
		assertProblem(band.send(HttpMethod.PUT, "/api/variants/" + gg, """
				{"version": 1, "size": "GG", "sku": "cam-m"}""", headers -> headers.setBearerAuth(admin)),
				HttpStatus.CONFLICT, "SKU_TAKEN");
		assertProblem(band.send(HttpMethod.PUT, "/api/variants/" + gg, """
				{"version": 0, "size": "GG"}""", headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT,
				"VERSION_CONFLICT");

		String catalog = band.get("/api/products/" + productId, headers -> headers.setBearerAuth(member)).getBody();
		assertThat(JsonPath.<List<String>>read(catalog, "$.variants[*].size")).containsExactly("M", "G", "GG", "XG");
	}

	@Test
	void archivedProductsAndVariantsLeaveTheActiveListing() {
		String shirt = create(SHIRT).getBody();
		String productId = JsonPath.read(shirt, "$.id");
		String g = JsonPath.read(shirt, "$.variants[1].id");
		create("""
				{"name": "Patch", "currency": "BRL", "defaultPriceCents": 1500}""");

		assertThat(post("/api/variants/" + g + "/archive").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(activeSizes()).containsExactly("M", "GG", null);

		assertThat(post("/api/products/" + productId + "/archive").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(activeSizes()).containsExactly((String) null);
		String all = band.get("/api/products?status=ALL", headers -> headers.setBearerAuth(member)).getBody();
		assertThat(JsonPath.<List<Boolean>>read(all, "$[*].archived")).containsExactly(true, false);
		assertThat(JsonPath.<List<Boolean>>read(all, "$[0].variants[*].archived")).containsExactly(false, true, false);

		post("/api/products/" + productId + "/unarchive");
		post("/api/variants/" + g + "/unarchive");
		assertThat(activeSizes()).containsExactly("M", "G", "GG", null);
		assertThat(auditActions("%archived")).containsExactly("product.archived", "product.unarchived",
				"product_variant.archived", "product_variant.unarchived");
	}

	@Test
	void productsAndVariantsWithoutHistoryCanBeDeleted() {
		String shirt = create(SHIRT).getBody();
		String productId = JsonPath.read(shirt, "$.id");
		String m = JsonPath.read(shirt, "$.variants[0].id");
		String patch = create("""
				{"name": "Patch", "currency": "BRL", "defaultPriceCents": 1500}""").getBody();

		assertThat(band.send(HttpMethod.DELETE, "/api/variants/" + m + "?version=0", null,
				headers -> headers.setBearerAuth(admin)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertProblem(band.send(HttpMethod.DELETE, "/api/variants/" + JsonPath.read(patch, "$.variants[0].id"), null,
				headers -> headers.setBearerAuth(admin)), HttpStatus.CONFLICT, "LAST_VARIANT");

		assertThat(band.send(HttpMethod.DELETE, "/api/products/" + productId, null,
				headers -> headers.setBearerAuth(admin)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(band.get("/api/products/" + productId, headers -> headers.setBearerAuth(admin)).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(OwnerSql.superuserQueryForString("SELECT count(*) FROM bandcore.product_variants"))
			.isEqualTo("1");
		assertThat(auditActions("%deleted")).containsExactly("product.deleted", "product_variant.deleted");
	}

	@Test
	void membersReadTheCatalogButOnlyAdminsChangeIt() {
		assertProblem(band.postJson("/api/products", SHIRT, headers -> headers.setBearerAuth(member)),
				HttpStatus.FORBIDDEN, "ACCESS_DENIED");
		String id = JsonPath.read(create(SHIRT).getBody(), "$.id");
		assertThat(band.get("/api/products/" + id, headers -> headers.setBearerAuth(member)).getStatusCode())
			.isEqualTo(HttpStatus.OK);
		assertProblem(band.patchJson("/api/products/" + id, "{\"version\": 0, \"name\": \"X\"}",
				headers -> headers.setBearerAuth(member)), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
	}

	private List<String> activeSizes() {
		String active = band.get("/api/products?status=ACTIVE", headers -> headers.setBearerAuth(member)).getBody();
		return JsonPath.read(active, "$[*].variants[*].size");
	}

	private ResponseEntity<String> create(String json) {
		return band.postJson("/api/products", json, headers -> headers.setBearerAuth(admin));
	}

	private ResponseEntity<String> post(String path) {
		return band.send(HttpMethod.POST, path, null, headers -> headers.setBearerAuth(admin));
	}

	private static void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
		assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
		assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo(code);
	}

	private static List<String> auditActions(String like) {
		String actions = OwnerSql.superuserQueryForString(
				"SELECT string_agg(action, ',' ORDER BY action) FROM bandcore.audit_events WHERE action LIKE '%s'"
					.formatted(like));
		return actions == null ? List.of() : List.of(actions.split(","));
	}

}
