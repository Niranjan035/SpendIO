package com.spendio.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Layered architecture and module dependency rules from ARCHITECTURE.md (section B.3).
 *
 * <p>Modules are discovered from the imported classes, so a module added in a later phase is
 * covered without editing this file. Cross-module edges are the explicit allow-list from B.3:
 * an edge that is not listed there is a coupling the architecture did not sanction.
 */
class ArchUnitTests {

    private static final String ROOT = "com.spendio";

    /** Packages every module may depend on freely. */
    private static final Set<String> KERNEL_PACKAGES =
            Set.of("config", "sharedkernel", "architecture", "support");

    /**
     * Allowed module edges, transcribed from ARCHITECTURE.md B.3. A key may depend on the values
     * listed for it and on every {@link #KERNEL_PACKAGES kernel} package; anything else is a rule
     * violation. Edges only appear in the diagram once the module exists in a later phase, so
     * unknown modules simply have no allow-list and are permitted to reach the kernel only.
     */
    private static final Map<String, Set<String>> ALLOWED_EDGES =
            Map.ofEntries(
                    Map.entry("auth", Set.of("users", "reference", "mail")),
                    Map.entry("users", Set.of("reference")),
                    Map.entry("reference", Set.of("fx")),
                    Map.entry("fx", Set.of("reference")),
                    Map.entry("accounts", Set.of("reference")),
                    Map.entry("categories", Set.of("reference")),
                    Map.entry(
                            "transactions",
                            Set.of("accounts", "categories", "fx", "notifications", "audit")),
                    Map.entry(
                            "recurring",
                            Set.of("transactions", "accounts", "categories", "notifications")),
                    Map.entry("budgets", Set.of("transactions", "categories", "notifications")),
                    Map.entry("goals", Set.of("transactions", "accounts", "notifications")),
                    Map.entry("imports", Set.of("transactions", "accounts", "categories")),
                    Map.entry("notifications", Set.of("users")),
                    Map.entry(
                            "analytics",
                            Set.of("transactions", "budgets", "goals", "accounts", "recurring")),
                    Map.entry("audit", Set.of("users")));

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(ROOT);

    private static Set<String> featureModules() {
        Set<String> modules = new TreeSet<>();
        for (JavaClass javaClass : CLASSES) {
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(ROOT + ".")) {
                continue;
            }
            String relative = packageName.substring(ROOT.length() + 1);
            int dot = relative.indexOf('.');
            String topLevel = dot == -1 ? relative : relative.substring(0, dot);
            if (!KERNEL_PACKAGES.contains(topLevel)) {
                modules.add(topLevel);
            }
        }
        return modules;
    }

    private static void check(ArchRule rule) {
        rule.check(CLASSES);
    }

    private static void checkAll(List<ArchRule> rules, String heading) {
        List<String> violations = new ArrayList<>();
        for (ArchRule rule : rules) {
            try {
                rule.check(CLASSES);
            } catch (AssertionError | RuntimeException ex) {
                violations.add(rule.getDescription() + System.lineSeparator() + ex.getMessage());
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(heading + System.lineSeparator() + String.join(System.lineSeparator(), violations));
        }
    }

    @Test
    void controllers_do_not_touch_repositories() {
        check(
                noClasses()
                        .that()
                        .haveSimpleNameEndingWith("Controller")
                        .or()
                        .resideInAPackage("..controller..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..repository..")
                        .because("controllers call services; repositories stay behind the service layer"));
    }

    @Test
    void controllers_live_in_a_controller_package() {
        check(
                classes()
                        .that()
                        .haveSimpleNameEndingWith("Controller")
                        .should()
                        .resideInAPackage("..controller..")
                        .because("one package convention keeps the API surface discoverable"));
    }

    @Test
    void jpa_entities_live_in_a_domain_package() {
        check(
                noClasses()
                        .that()
                        .areAnnotatedWith(Entity.class)
                        .or()
                        .areAnnotatedWith(Embeddable.class)
                        .should()
                        .resideOutsideOfPackage("..domain..")
                        .because("entities never leave their module (B.3)"));
    }

    @Test
    void repositories_are_interfaces() {
        check(
                classes()
                        .that()
                        .resideInAPackage("..repository..")
                        .and()
                        .areTopLevelClasses()
                        .should()
                        .beInterfaces()
                        .because("a repository contract is an interface so it can be mocked and swapped; "
                                + "nested value records are part of that contract"));
    }

    @Test
    void no_package_cycles() {
        check(
                slices()
                        .matching(ROOT + ".(*)..")
                        .should()
                        .beFreeOfCycles()
                        .because("cycles between packages make extraction and testing impossible"));
    }

    @Test
    void module_dependencies_stay_within_the_declared_edges() {
        Set<String> modules = featureModules();
        List<ArchRule> rules = new ArrayList<>();
        for (String source : modules) {
            Set<String> allowed = new TreeSet<>(ALLOWED_EDGES.getOrDefault(source, Set.of()));
            for (String target : modules) {
                if (source.equals(target) || allowed.contains(target)) {
                    continue;
                }
                rules.add(
                        noClasses()
                                .that()
                                .resideInAPackage(ROOT + "." + source + "..")
                                .should()
                                .dependOnClassesThat()
                                .resideInAPackage(ROOT + "." + target + "..")
                                .because(source + " -> " + target + " is not an edge declared in ARCHITECTURE.md B.3"));
            }
        }
        checkAll(rules, "Undeclared module edges:");
    }

    @Test
    void modules_do_not_reach_into_another_modules_repository() {
        Set<String> modules = featureModules();
        List<ArchRule> rules = new ArrayList<>();
        for (String source : modules) {
            for (String target : modules) {
                if (source.equals(target)) {
                    continue;
                }
                rules.add(
                        noClasses()
                                .that()
                                .resideInAPackage(ROOT + "." + source + "..")
                                .should()
                                .dependOnClassesThat()
                                .resideInAPackage(ROOT + "." + target + "..repository..")
                                .because("cross-module calls go through that module's service (B.3)"));
            }
        }
        checkAll(rules, "Cross-module repository access:");
    }

    @Test
    void the_shared_kernel_does_not_depend_on_feature_modules() {
        List<ArchRule> rules = new ArrayList<>();
        for (String target : featureModules()) {
            rules.add(
                    noClasses()
                            .that()
                            .resideInAPackage(ROOT + ".sharedkernel..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAPackage(ROOT + "." + target + "..")
                            .because("the kernel must stay importable by every module, never the reverse"));
        }
        checkAll(rules, "Kernel dependencies:");
    }
}
