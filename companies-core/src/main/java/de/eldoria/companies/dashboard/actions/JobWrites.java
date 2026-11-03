/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.job.JobKind;
import de.eldoria.companies.components.job.JobState;
import de.eldoria.companies.configuration.elements.jobs.JobSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobLand;
import de.eldoria.companies.data.wrapper.job.JobReview;
import de.eldoria.companies.data.wrapper.job.JobTarget;
import de.eldoria.companies.data.wrapper.job.StoredArea;
import de.eldoria.companies.jobs.AreaChecks;
import de.eldoria.companies.jobs.AreaWorld;
import de.eldoria.companies.jobs.Blueprint;
import de.eldoria.companies.jobs.BlueprintItems;
import de.eldoria.companies.jobs.JobService;
import de.eldoria.companies.jobs.JobServices;
import de.eldoria.companies.jobs.SchematicUploads;
import de.eldoria.companies.jobs.TerraformCheck;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.web.api.Actions;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The dashboard's Jobs page, as {@code /order job} and {@code /company job} offer it: the open jobs, the player's own
 * jobs and those of their company, a job's details, posting a contract, and the steps of a job's life that need no
 * world: describing, approving, rejecting, disputing, withdrawing a dispute, cancelling and ending a harvest for the
 * owner; taking, giving back, saying a contract is done and ending a harvest for the company. Area jobs are posted and
 * checked in game, as they need a selection and the world. The owner of a job is never named to companies, and the
 * company working on a job never to its owner. A contract posted here has no place to look at. The methods block on
 * the database and are called off the main thread.
 */
public final class JobWrites {
    /**
     * The actions performed here.
     */
    public static final Set<String> ACTIONS = Set.of(
            Actions.JOBS_LIST,
            Actions.JOBS_INFO,
            Actions.JOBS_CONTRACT,
            Actions.JOBS_DESCRIBE,
            Actions.JOBS_APPROVE,
            Actions.JOBS_REJECT,
            Actions.JOBS_DISPUTE,
            Actions.JOBS_WITHDRAW,
            Actions.JOBS_CANCEL,
            Actions.JOBS_END,
            Actions.JOBS_TAKE,
            Actions.JOBS_GIVE_BACK,
            Actions.JOBS_DONE,
            Actions.JOBS_EXTEND,
            Actions.JOBS_EXTENSION_ASK,
            Actions.JOBS_EXTENSION_GRANT,
            Actions.JOBS_EXTENSION_REFUSE,
            Actions.JOBS_CANCEL_ACCEPT,
            Actions.JOBS_CANCEL_REFUSE,
            Actions.JOBS_COLLECT,
            Actions.JOBS_SCHEMATICS_LIST,
            Actions.JOBS_SCHEMATICS_UPLOAD,
            Actions.JOBS_SCHEMATICS_DELETE,
            Actions.JOBS_AREAS_LIST,
            Actions.JOBS_AREAS_DELETE,
            Actions.JOBS_AREAS_POST,
            Actions.JOBS_AREAS_PREVIEW,
            Actions.JOBS_AREAS_SCHEMATIC,
            Actions.JOBS_AREAS_BUILD,
            Actions.JOBS_DRAFTS_POST,
            Actions.JOBS_DRAFTS_EDIT,
            Actions.JOBS_DRAFTS_DELETE,
            Actions.JOBS_DRAFTS_SUPPLY);

    /**
     * The most jobs of each list.
     */
    static final int LISTED = 50;

    private static final String OWNER = "order.job.";
    private static final JobArea NOWHERE = new JobArea("", 0, 0, 0, 0, 0, 0);
    private static final String COMPANY = "company.job.";
    private static final long WORLD_SECONDS = 15;

    private final Sources sources;
    private final Answers answers;

    /**
     * @param sources what jobs are read and changed with
     */
    public JobWrites(Sources sources) {
        this.sources = sources;
        answers = new Answers(sources.render());
    }

    /**
     * Performs an action of the Jobs page.
     *
     * @param request the action, one of {@link #ACTIONS}
     * @param session the player's session
     * @param args    the arguments
     * @return the result
     */
    ActionResult perform(ActionRequest request, StoredSession session, JsonNode args) {
        var jobs = sources.jobs().get();
        if (jobs == null) return answers.refused(request, "dashboard.unsupported");
        var player = session.player();
        var company = sources.companyOf().apply(player).join();
        var id = args.path("job").asInt(0);
        var service = jobs.service();
        return switch (request.name()) {
            case Actions.JOBS_LIST -> answers.data(request, list(jobs, session, company));
            case Actions.JOBS_INFO -> info(request, jobs, player, company, id);
            case Actions.JOBS_CONTRACT -> contract(request, jobs, session, args);
            case Actions.JOBS_DESCRIBE ->
                owner(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.describe(player, id, args.path("text").asString("")));
            case Actions.JOBS_APPROVE ->
                owner(request, jobs, player, company, id, service.approve(player, session.playerName(), id));
            case Actions.JOBS_REJECT ->
                owner(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.reject(player, id, args.path("reason").asString("")));
            case Actions.JOBS_EXTEND ->
                extension(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.extend(player, id, args.path("hours").asInt(0)));
            case Actions.JOBS_EXTENSION_GRANT ->
                extension(request, jobs, player, company, id, service.grantExtension(player, id));
            case Actions.JOBS_EXTENSION_REFUSE ->
                extension(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        service.refuseExtension(player, id, args.path("reason").asString("")));
            case Actions.JOBS_EXTENSION_ASK ->
                forCompany(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        c -> service.askExtension(
                                c,
                                player,
                                id,
                                args.path("hours").asInt(0),
                                args.path("reason").asString("")));
            case Actions.JOBS_CANCEL ->
                owner(request, jobs, player, company, id, service.cancel(player, session.playerName(), id));
            case Actions.JOBS_COLLECT -> collect(request, jobs, player, company, id);
            case Actions.JOBS_SCHEMATICS_LIST -> answers.data(request, schematics(jobs, player));
            case Actions.JOBS_SCHEMATICS_UPLOAD -> upload(request, jobs, session, args);
            case Actions.JOBS_SCHEMATICS_DELETE -> {
                jobs.uploads().delete(player, args.path("name").asString(""));
                yield answers.data(request, schematics(jobs, player));
            }
            case Actions.JOBS_AREAS_LIST ->
                mayPost(session)
                        ? answers.data(request, areas(jobs, player))
                        : answers.refused(request, "error.permission.postJob");
            case Actions.JOBS_AREAS_DELETE -> {
                if (!mayPost(session)) yield answers.refused(request, "error.permission.postJob");
                jobs.areas().delete(player, args.path("name").asString(""));
                yield answers.data(request, areas(jobs, player));
            }
            case Actions.JOBS_AREAS_POST -> postOnArea(request, jobs, session, args);
            case Actions.JOBS_AREAS_PREVIEW -> preview(request, jobs, session, args);
            case Actions.JOBS_AREAS_SCHEMATIC -> schematicFit(request, jobs, session, args);
            case Actions.JOBS_AREAS_BUILD -> build(request, jobs, session, args);
            case Actions.JOBS_DRAFTS_POST -> postDraft(request, jobs, session, args);
            case Actions.JOBS_DRAFTS_EDIT -> editDraft(request, jobs, player, args);
            case Actions.JOBS_DRAFTS_DELETE -> deleteDraft(request, jobs, player, args);
            case Actions.JOBS_DRAFTS_SUPPLY -> supply(request, jobs, player, args);
            case Actions.JOBS_CANCEL_ACCEPT ->
                forCompany(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        c -> service.answerCancel(c, player, session.playerName(), id, true));
            case Actions.JOBS_CANCEL_REFUSE ->
                forCompany(
                        request,
                        jobs,
                        player,
                        company,
                        id,
                        c -> service.answerCancel(c, player, session.playerName(), id, false));
            case Actions.JOBS_DISPUTE -> {
                var reason = args.path("reason").asString("");
                yield owns(jobs, player, id)
                        ? owner(request, jobs, player, company, id, service.disputeAsOwner(player, id, reason))
                        : forCompany(
                                request,
                                jobs,
                                player,
                                company,
                                id,
                                c -> service.disputeAsCompany(c, player, id, reason));
            }
            case Actions.JOBS_WITHDRAW ->
                owns(jobs, player, id)
                        ? owner(request, jobs, player, company, id, service.withdrawAsOwner(player, id))
                        : forCompany(request, jobs, player, company, id, c -> service.withdrawAsCompany(c, player, id));
            case Actions.JOBS_END -> {
                jobs.access().flush(id);
                yield owns(jobs, player, id)
                        ? owner(
                                request,
                                jobs,
                                player,
                                company,
                                id,
                                service.endAsOwner(player, session.playerName(), id))
                        : forCompany(request, jobs, player, company, id, c -> service.endAsCompany(c, player, id));
            }
            case Actions.JOBS_TAKE -> forCompany(request, jobs, player, company, id, c -> service.take(c, player, id));
            case Actions.JOBS_GIVE_BACK -> {
                var result = forCompany(request, jobs, player, company, id, c -> service.giveBack(c, player, id));
                if (result.ok()) jobs.access().forget(id);
                yield result;
            }
            case Actions.JOBS_DONE ->
                forCompany(request, jobs, player, company, id, c -> service.markDone(c, player, id));
            default -> answers.refused(request, "dashboard.unsupported");
        };
    }

    private JobList list(JobServices jobs, StoredSession session, Optional<CompanyProfile> company) {
        var player = session.player();
        var data = jobs.data();
        var open = data.open(sources.now().get(), LISTED).stream()
                .filter(job -> company.map(found -> found.member(job.owner()).isEmpty())
                        .orElse(true))
                .map(job -> row(job, player, company))
                .toList();
        var mine = data.ofOwner(player, LISTED).stream()
                .map(job -> row(job, player, company))
                .toList();
        var ours = company.map(found -> data.ofCompany(found.id()).stream()
                        .map(job -> row(job, player, company))
                        .toList())
                .orElse(List.of());
        return new JobList(
                open,
                mine,
                ours,
                company.isPresent(),
                canManage(company, player),
                session.permissions().contains(Permission.Orders.JOBS)
                        && sources.settings().get().enabled());
    }

    private ActionResult info(
            ActionRequest request, JobServices jobs, UUID player, Optional<CompanyProfile> company, int id) {
        var job = visible(jobs, player, company, id);
        if (job.isEmpty()) return answers.refused(request, CommandFailure.of(COMPANY + "error.unknown", number(id)));
        return answers.data(request, details(jobs, job.get(), player, company));
    }

    private ActionResult contract(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Orders.JOBS)) {
            return answers.refused(request, "error.permission.postJob");
        }
        var title = args.path("title").asString("");
        var description = args.path("description").asString("").strip();
        if (description.length() > Job.MAX_DESCRIPTION) {
            return answers.refused(
                    request, CommandFailure.of(OWNER + "error.description", Values.number("max", Job.MAX_DESCRIPTION)));
        }
        var place = NOWHERE;
        var node = jobs.node();
        var areaName = args.path("area").asString("");
        if (!areaName.isEmpty()) {
            var stored = jobs.areas().area(session.player(), areaName);
            if (stored.isEmpty()) {
                return answers.refused(
                        request, CommandFailure.of(OWNER + "area.error.unknown", Values.text("area", areaName)));
            }
            place = stored.get().area();
            node = stored.get().node();
        } else if (args.path("place").asBoolean(false)) {
            var last = sources.lastPlace().apply(session.player());
            if (last.isEmpty()) return answers.refused(request, OWNER + "error.noPlace");
            place = last.get();
        }
        if (args.path("draft").asBoolean(false)) {
            return posted(
                    request,
                    jobs,
                    session.player(),
                    JobKind.CONTRACT,
                    jobs.service()
                            .saveDraft(
                                    session.player(),
                                    JobKind.CONTRACT,
                                    title,
                                    args.path("price").asDouble(0),
                                    args.path("hours").asInt(0),
                                    node,
                                    place,
                                    JobService.Details.contract(description)));
        }
        var posted = jobs.service()
                .post(
                        session.player(),
                        JobKind.CONTRACT,
                        title,
                        args.path("price").asDouble(0),
                        args.path("hours").asInt(0),
                        node,
                        place,
                        JobLand.NONE,
                        null,
                        JobService.Details.contract(description));
        return posted(request, jobs, session.player(), JobKind.CONTRACT, posted);
    }

    private ActionResult posted(
            ActionRequest request, JobServices jobs, UUID player, JobKind kind, JobService.Posted posted) {
        return switch (posted) {
            case JobService.Posted.Done done ->
                answers.data(request, details(jobs, done.job(), player, Optional.empty()));
            case JobService.Posted.Refused refused ->
                answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "error." + camel(refused.refusal().name()),
                                Values.number(
                                        "max",
                                        JobService.limit(
                                                refused.refusal(),
                                                kind,
                                                sources.settings().get()))));
        };
    }

    /**
     * Moves the stock of the player's harvest job to their item returns, where they take it in game like a delivered
     * order. What can not be stored goes back into the stock.
     */
    private ActionResult collect(
            ActionRequest request, JobServices jobs, UUID player, Optional<CompanyProfile> company, int id) {
        var job = jobs.data()
                .job(id)
                .filter(found -> found.owner().equals(player))
                .orElse(null);
        if (job == null || job.kind() != JobKind.HARVEST) {
            return answers.refused(request, CommandFailure.of(OWNER + "error.unknown", number(id)));
        }
        Map<String, Integer> taken = new LinkedHashMap<>();
        jobs.data().stock(id).forEach((material, amount) -> {
            if (jobs.data().takeStock(id, material, amount)) taken.put(material, amount);
        });
        if (taken.isEmpty()) return answers.refused(request, CommandFailure.of(OWNER + "stock.empty", number(id)));
        Map<String, Integer> left;
        try {
            left = sources.returns().apply(player, taken);
        } catch (RuntimeException e) {
            left = taken;
        }
        if (!left.isEmpty()) {
            jobs.data().stock(id, left);
            var key = left.size() < taken.size() ? "error.collectPartial" : "error.failed";
            return answers.refused(request, CommandFailure.of(OWNER + key, number(id)));
        }
        return answers.data(request, details(jobs, job, player, company));
    }

    private AreaList areas(JobServices jobs, UUID player) {
        var areas = jobs.areas();
        return new AreaList(
                areas.max(),
                areas.areas(player).stream()
                        .map(stored -> {
                            var area = stored.area();
                            return new AreaRow(
                                    stored.name(),
                                    stored.node().toString(),
                                    area.world(),
                                    area.center(),
                                    area.maxX() - area.minX() + 1,
                                    area.maxY() - area.minY() + 1,
                                    area.maxZ() - area.minZ() + 1,
                                    area.volume());
                        })
                        .toList());
    }

    /**
     * Posts a dig, terraform or harvest job on one of the player's stored areas. The gateway routes the call to the
     * server whose world holds the area, where its height and land are checked on the main thread as in game.
     */
    private ActionResult postOnArea(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        var found = storedHere(request, jobs, session, args);
        if (found.refused() != null) return found.refused();
        var area = found.area().area();
        JobKind kind;
        JobService.Details details;
        switch (args.path("kind").asString("")) {
            case "dig" -> {
                kind = JobKind.DIG;
                details = JobService.Details.NONE;
            }
            case "terraform" -> {
                if (!args.path("y").isInt()) return answers.refused(request, "dashboard.unsupported");
                String surface = null;
                var block = args.path("surface").asString("");
                if (!block.isEmpty()) {
                    var material = TerraformCheck.block(block);
                    if (material == null) {
                        return answers.refused(
                                request, CommandFailure.of(OWNER + "error.block", Values.text("block", block)));
                    }
                    surface = material.name();
                }
                kind = JobKind.TERRAFORM;
                details = JobService.Details.shape(new JobTarget(args.path("y").asInt(), surface));
            }
            case "harvest" -> {
                kind = JobKind.HARVEST;
                details = JobService.Details.harvest(args.path("unitPrice").asDouble(0));
            }
            default -> {
                return answers.refused(request, "dashboard.unsupported");
            }
        }
        return checkedPost(request, jobs, session.player(), kind, area, args, details);
    }

    /**
     * Draws the top layer of one of the player's stored areas and lists the schematics a build job on it may use.
     */
    private ActionResult preview(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        var found = storedHere(request, jobs, session, args);
        if (found.refused() != null) return found.refused();
        var stored = found.area();
        if (!AreaWorld.drawable(stored.area())) {
            return answers.refused(
                    request,
                    CommandFailure.of(OWNER + "area.error.tooLarge", Values.number("max", AreaWorld.MAX_SIDE)));
        }
        AreaWorld.Drawn drawn;
        try {
            drawn = jobs.world()
                    .topLayer(session.player(), stored.area())
                    .orTimeout(WORLD_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            return answers.refused(request, OWNER + "error.failed");
        }
        AreaWorld.TopLayer top;
        switch (drawn) {
            case AreaWorld.Drawn.Done done -> top = done.top();
            case AreaWorld.Drawn.Busy busy -> {
                return answers.refused(request, OWNER + "area.error.drawing");
            }
            case AreaWorld.Drawn.NoWorld noWorld -> {
                return answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "error.noWorld",
                                Values.text("world", stored.area().world())));
            }
        }
        var names = jobs.schematics().names(session.player());
        return answers.data(
                request,
                new AreaPreview(
                        stored.name(),
                        top.width(),
                        top.length(),
                        top.palette(),
                        top.cells(),
                        names.server(),
                        names.own()));
    }

    /**
     * Reads the size and cost of a schematic the player may build, turned.
     */
    private ActionResult schematicFit(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        if (!mayPost(session)) return answers.refused(request, "error.permission.postJob");
        var blueprint = blueprint(request, jobs, session.player(), args);
        if (blueprint.refused() != null) return blueprint.refused();
        var read = blueprint.blueprint();
        var size = read.size();
        var cost = jobs.costs().of(read);
        return answers.data(
                request,
                new SchematicFit(
                        size[0],
                        size[1],
                        size[2],
                        read.blocks(),
                        cost.total(),
                        cost.unpriced().size(),
                        BlueprintItems.of(read).unmakeable().stream()
                                .map(type -> type.getKey().getKey())
                                .sorted()
                                .toList()));
    }

    /**
     * Posts a build job of a schematic placed on one of the player's stored areas. It stands on the ground of its
     * origin's column, as if the owner stood there in game; a price below what its blocks cost needs {@code confirm}.
     */
    private ActionResult build(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        var found = storedHere(request, jobs, session, args);
        if (found.refused() != null) return found.refused();
        var blueprint = blueprint(request, jobs, session.player(), args);
        if (blueprint.refused() != null) return blueprint.refused();
        AreaWorld.Placed placed;
        try {
            placed = jobs.world()
                    .place(
                            found.area().area(),
                            blueprint.blueprint(),
                            args.path("x").asInt(),
                            args.path("z").asInt())
                    .orTimeout(WORLD_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            return answers.refused(request, OWNER + "error.failed");
        }
        return switch (placed) {
            case AreaWorld.Placed.Outside outside -> answers.refused(request, OWNER + "area.error.outside");
            case AreaWorld.Placed.NoWorld noWorld ->
                answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "error.noWorld",
                                Values.text("world", found.area().area().world())));
            case AreaWorld.Placed.Nothing nothing -> schematicMissing(request, args);
            case AreaWorld.Placed.Done done -> {
                var cost = jobs.costs().of(done.blueprint());
                var price = args.path("price").asDouble(0);
                if (cost.above(price)
                        && !args.path("confirm").asBoolean(false)
                        && !args.path("supplied").asBoolean(false)) {
                    yield answers.refused(
                            request, CommandFailure.of(OWNER + "error.belowCost", Values.number("cost", cost.total())));
                }
                yield checkedPost(
                        request,
                        jobs,
                        session.player(),
                        JobKind.BUILD,
                        done.area(),
                        args,
                        JobService.Details.build(done.blueprint()));
            }
        };
    }

    /**
     * Checks an area on the main thread as in game and posts the job when it may lie there.
     */
    private ActionResult checkedPost(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            JobKind kind,
            JobArea area,
            JsonNode args,
            JobService.Details details) {
        var supplied = args.path("supplied").asBoolean(false);
        if (supplied || args.path("draft").asBoolean(false)) {
            return posted(
                    request,
                    jobs,
                    player,
                    kind,
                    jobs.service()
                            .saveDraft(
                                    player,
                                    kind,
                                    args.path("title").asString(""),
                                    args.path("price").asDouble(0),
                                    args.path("hours").asInt(0),
                                    jobs.node(),
                                    area,
                                    details,
                                    supplied));
        }
        return withLand(
                request,
                jobs,
                player,
                area,
                land -> posted(
                        request,
                        jobs,
                        player,
                        kind,
                        jobs.service()
                                .post(
                                        player,
                                        kind,
                                        args.path("title").asString(""),
                                        args.path("price").asDouble(0),
                                        args.path("hours").asInt(0),
                                        jobs.node(),
                                        area,
                                        land.land(),
                                        land.ref(),
                                        details)));
    }

    /**
     * Checks on the main thread that an area may hold a job of the player and hands on the land it lies on, or answers
     * why not.
     */
    private ActionResult withLand(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            JobArea area,
            Function<AreaChecks.Checked.Land, ActionResult> then) {
        AreaChecks.Checked checked;
        try {
            checked = jobs.checks().checkOnMain(player, area);
        } catch (CompletionException e) {
            return answers.refused(request, OWNER + "error.failed");
        }
        return switch (checked) {
            case AreaChecks.Checked.NoWorld noWorld ->
                answers.refused(
                        request, CommandFailure.of(OWNER + "error.noWorld", Values.text("world", noWorld.world())));
            case AreaChecks.Checked.Height height ->
                answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "error.height",
                                Values.number("min", height.min()),
                                Values.number("max", height.max())));
            case AreaChecks.Checked.Foreign foreign ->
                answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "error.foreign",
                                Values.text("land", Objects.requireNonNullElse(foreign.ref(), "?"))));
            case AreaChecks.Checked.Unowned unowned -> answers.refused(request, OWNER + "error.unowned");
            case AreaChecks.Checked.Land land -> then.apply(land);
        };
    }

    /**
     * Posts one of the player's drafts on this server; an area's land is checked again where it lies now.
     */
    private ActionResult postDraft(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        if (!mayPost(session)) return answers.refused(request, "error.permission.postJob");
        var player = session.player();
        var id = args.path("id").asInt(0);
        var draft = jobs.data().job(id).filter(job -> job.owner().equals(player) && job.state() == JobState.DRAFT);
        if (draft.isEmpty()) return answers.refused(request, OWNER + "error.noDraft");
        var job = draft.get();
        if (!job.kind().area()) {
            return posted(request, jobs, player, job.kind(), jobs.service().publish(player, id, JobLand.NONE, null));
        }
        if (!job.node().equals(jobs.node())) {
            return answers.refused(request, CommandFailure.of(OWNER + "error.otherServer", number(id)));
        }
        return withLand(
                request,
                jobs,
                player,
                job.area(),
                land -> posted(
                        request,
                        jobs,
                        player,
                        job.kind(),
                        jobs.service().publish(player, id, land.land(), land.ref())));
    }

    private ActionResult editDraft(ActionRequest request, JobServices jobs, UUID player, JsonNode args) {
        var id = args.path("id").asInt(0);
        var description = args.path("description").asString("").strip();
        var kind = jobs.data().job(id).map(Job::kind).orElse(JobKind.CONTRACT);
        return posted(
                request,
                jobs,
                player,
                kind,
                jobs.service()
                        .editDraft(
                                player,
                                id,
                                args.path("title").asString(""),
                                description.isEmpty() ? null : description,
                                args.path("price").asDouble(0),
                                args.path("hours").asInt(0)));
    }

    private ActionResult deleteDraft(ActionRequest request, JobServices jobs, UUID player, JsonNode args) {
        var id = args.path("id").asInt(0);
        var draft = jobs.data().job(id).filter(job -> job.owner().equals(player) && job.state() == JobState.DRAFT);
        if (draft.isEmpty()) return answers.refused(request, OWNER + "error.noDraft");
        if (!jobs.service().deleteDraft(player, id, sources.returns())) {
            return answers.refused(request, CommandFailure.of(OWNER + "error.draftKept", number(id)));
        }
        return answers.data(request, Map.of("id", id));
    }

    /**
     * Finds the stored area named in {@code area} and makes sure it lies on this server and the player may post.
     */
    private Found storedHere(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        if (!mayPost(session)) return Found.refused(answers.refused(request, "error.permission.postJob"));
        var name = args.path("area").asString("");
        var stored = jobs.areas().area(session.player(), name);
        if (stored.isEmpty()) {
            return Found.refused(answers.refused(
                    request, CommandFailure.of(OWNER + "area.error.unknown", Values.text("area", name))));
        }
        if (!stored.get().node().equals(jobs.node())) {
            return Found.refused(answers.refused(
                    request, CommandFailure.of(OWNER + "area.error.otherServer", Values.text("area", name))));
        }
        return new Found(stored.get(), null);
    }

    /**
     * Reads the schematic named in {@code schematic}, turned by {@code rotation}.
     */
    private Read blueprint(ActionRequest request, JobServices jobs, UUID player, JsonNode args) {
        if (!jobs.hooks().selects()) return new Read(null, answers.refused(request, OWNER + "error.noBlueprints"));
        var rotation = args.path("rotation").asInt(-1);
        if (rotation % 90 != 0 || rotation < 0 || rotation >= 360) {
            return new Read(null, answers.refused(request, OWNER + "error.rotation"));
        }
        var maxVolume = sources.settings().get().buildMaxVolume();
        return jobs.schematics()
                .blueprint(player, args.path("schematic").asString(""), rotation, maxVolume)
                .map(found -> new Read(found, null))
                .orElseGet(() -> new Read(null, schematicMissing(request, args)));
    }

    private ActionResult schematicMissing(ActionRequest request, JsonNode args) {
        return answers.refused(
                request,
                CommandFailure.of(
                        OWNER + "error.schematic",
                        Values.text("schematic", args.path("schematic").asString("")),
                        Values.number("max", sources.settings().get().buildMaxVolume())));
    }

    /**
     * A stored area of the player on this server, or the answer refusing it; the area is read only when nothing refused
     * it.
     */
    private record Found(
            @Nullable StoredArea area, @Nullable ActionResult refused) {
        private static Found refused(ActionResult refused) {
            return new Found(null, refused);
        }

        @Override
        public StoredArea area() {
            return Objects.requireNonNull(area);
        }
    }

    /**
     * A schematic read as a blueprint, or the answer refusing it; the blueprint is read only when nothing refused it.
     */
    private record Read(
            @Nullable Blueprint blueprint, @Nullable ActionResult refused) {
        @Override
        public Blueprint blueprint() {
            return Objects.requireNonNull(blueprint);
        }
    }

    private static boolean mayPost(StoredSession session) {
        return session.permissions().contains(Permission.Orders.JOBS);
    }

    private SchematicList schematics(JobServices jobs, UUID player) {
        var uploads = jobs.uploads();
        return new SchematicList(
                uploads.open(),
                sources.settings().get().maxSchematics(),
                SchematicUploads.MAX_BYTES,
                uploads.names(player));
    }

    /**
     * Cleans and keeps a schematic the player uploaded as base64; it may then be used for build jobs by its name.
     */
    private ActionResult upload(ActionRequest request, JobServices jobs, StoredSession session, JsonNode args) {
        if (!session.permissions().contains(Permission.Orders.JOBS)) {
            return answers.refused(request, "error.permission.postJob");
        }
        byte[] file;
        try {
            file = Base64.getDecoder().decode(args.path("data").asString(""));
        } catch (IllegalArgumentException e) {
            return answers.refused(request, OWNER + "schematic.error.unreadable");
        }
        var uploaded = jobs.uploads().upload(session.player(), args.path("name").asString(""), file);
        return switch (uploaded) {
            case SchematicUploads.Uploaded.Stored stored ->
                answers.data(
                        request,
                        new Uploaded(
                                schematics(jobs, session.player()),
                                stored.name(),
                                stored.blocks(),
                                stored.cleaned().blocks(),
                                stored.cleaned().data(),
                                stored.cleaned().entities()));
            case SchematicUploads.Uploaded.Refused refused ->
                answers.refused(
                        request,
                        CommandFailure.of(
                                OWNER + "schematic.error."
                                        + camel(refused.reason().name()),
                                Values.number("max", sources.settings().get().maxSchematics()),
                                Values.number("size", SchematicUploads.MAX_BYTES / 1024)));
        };
    }

    private ActionResult owner(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            Optional<CompanyProfile> company,
            int id,
            JobService.Change change) {
        if (change == JobService.Change.DONE || change == JobService.Change.CANCEL_ASKED) {
            if (change == JobService.Change.DONE) jobs.access().checkSoon();
            return jobs.data()
                    .job(id)
                    .map(job -> answers.data(request, details(jobs, job, player, company)))
                    .orElseGet(() -> answers.refused(request, CommandFailure.of(OWNER + "error.unknown", number(id))));
        }
        var key =
                switch (change) {
                    case UNKNOWN, NOT_YOURS -> "error.unknown";
                    case ASKED -> "error.cancelAsked";
                    case TAKEN -> "error.taken";
                    case DRAFT -> "error.draft";
                    case DISPUTED -> "error.disputed";
                    case REASON -> "error.reason";
                    case TOO_LONG -> "error.description";
                    case NOT_DONE -> "error.notDone";
                    case HARVEST -> "error.harvest";
                    default -> "error.closed";
                };
        var max = change == JobService.Change.TOO_LONG ? Job.MAX_DESCRIPTION : JobReview.MAX_REASON;
        return answers.refused(request, CommandFailure.of(OWNER + key, number(id), Values.number("max", max)));
    }

    /**
     * Answers an owner's extension of a job: the job on success, otherwise the refusal worded for extensions, as the
     * in-game command words it.
     */
    private ActionResult extension(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            Optional<CompanyProfile> company,
            int id,
            JobService.Change change) {
        if (change == JobService.Change.DONE) return owner(request, jobs, player, company, id, change);
        var key =
                switch (change) {
                    case UNKNOWN, NOT_YOURS -> "error.unknown";
                    case DISABLED -> "error.extensionsOff";
                    case HOURS -> "error.extensionHours";
                    case NO_REQUEST -> "error.noExtensionRequest";
                    case REASON -> "error.extensionReason";
                    default -> "error.extensionClosed";
                };
        return answers.refused(
                request,
                CommandFailure.of(
                        OWNER + key,
                        number(id),
                        Values.number("max", JobReview.MAX_REASON),
                        Values.number("left", change == JobService.Change.HOURS ? left(jobs, id) : 0)));
    }

    private ActionResult forCompany(
            ActionRequest request,
            JobServices jobs,
            UUID player,
            Optional<CompanyProfile> company,
            int id,
            Function<CompanyProfile, JobService.Change> change) {
        if (company.isEmpty()) return answers.refused(request, COMPANY + "error.noCompany");
        var result = change.apply(company.get());
        if (result == JobService.Change.DONE) {
            jobs.access().checkSoon();
            var refreshed = sources.companyOf().apply(player).join();
            return jobs.data()
                    .job(id)
                    .map(job -> answers.data(request, details(jobs, job, player, refreshed)))
                    .orElseGet(
                            () -> answers.refused(request, CommandFailure.of(COMPANY + "error.unknown", number(id))));
        }
        var key =
                switch (result) {
                    case NO_PERMISSION -> "error.permission";
                    case UNKNOWN -> "error.unknown";
                    case LIMIT -> "error.limit";
                    case OWN -> "error.own";
                    case NOT_YOURS -> "error.notYours";
                    case DISPUTED -> "error.disputed";
                    case REASON -> "error.reason";
                    case HARVEST -> "error.harvest";
                    case AREA -> "error.inGame";
                    case DISABLED -> "error.extensionsOff";
                    case HOURS -> "error.extensionHours";
                    case ASKED -> "error.extensionAsked";
                    case REQUESTS -> "error.extensionRequests";
                    case REFUSED -> "error.refused";
                    case NO_REQUEST -> "error.noCancel";
                    default -> "error.closed";
                };
        return answers.refused(
                request,
                CommandFailure.of(
                        COMPANY + key,
                        number(id),
                        Values.number("length", JobReview.MAX_REASON),
                        Values.number("left", result == JobService.Change.HOURS ? left(jobs, id) : 0),
                        Values.number("max", result == JobService.Change.REQUESTS ? requests(jobs, id) : 0)));
    }

    private static int left(JobServices jobs, int id) {
        return jobs.data().job(id).map(job -> jobs.service().hoursLeft(job)).orElse(0);
    }

    private static int requests(JobServices jobs, int id) {
        return jobs.data().job(id).map(job -> job.extension().requests()).orElse(0);
    }

    private static boolean owns(JobServices jobs, UUID player, int id) {
        return jobs.data().job(id).map(job -> job.owner().equals(player)).orElse(false);
    }

    /**
     * A job the player may open: an open one, one of their own, or one their company holds.
     */
    private static Optional<Job> visible(JobServices jobs, UUID player, Optional<CompanyProfile> company, int id) {
        if (id <= 0) return Optional.empty();
        return jobs.data()
                .job(id)
                .filter(job -> job.state() == JobState.OPEN
                        || job.owner().equals(player)
                        || company.map(found -> Integer.valueOf(found.id()).equals(job.company()))
                                .orElse(false));
    }

    private JobRow row(Job job, UUID player, Optional<CompanyProfile> company) {
        var harvest = job.harvest();
        var area = job.area();
        return new JobRow(
                job.id(),
                job.kind().name().toLowerCase(Locale.ROOT),
                job.state().name().toLowerCase(Locale.ROOT),
                job.title(),
                job.price(),
                job.fee(),
                instant(job.due()),
                instant(job.expires()),
                job.kind().area() ? area.volume() : 0,
                area.world().isEmpty() ? null : area.world(),
                area.world().isEmpty() ? null : area.center(),
                harvest == null ? null : harvest.unitPrice(),
                harvest == null ? null : harvest.paid(),
                job.owner().equals(player),
                company.map(found -> Integer.valueOf(found.id()).equals(job.company()))
                        .orElse(false),
                job.state() == JobState.DRAFT && job.kind().area() ? job.node() : null,
                job.dueHours());
    }

    private JobDetails details(JobServices jobs, Job job, UUID player, Optional<CompanyProfile> company) {
        var review = job.review();
        var mine = job.owner().equals(player);
        var target = job.target();
        var doneAt = review.doneAt();
        var disputeBy = review.disputeBy();
        Map<String, Integer> stock = mine && (job.kind() == JobKind.HARVEST || job.supplied())
                ? jobs.data().stock(job.id())
                : Map.of();
        var settings = sources.settings().get();
        var extension = job.extension();
        var asked = job.state() == JobState.TAKEN ? extension.askedHours() : null;
        var extendable = settings.extensionsEnabled() && job.state() == JobState.TAKEN;
        return new JobDetails(
                row(job, player, company),
                job.description(),
                review.rejected(),
                review.dispute(),
                disputeBy != null && disputeBy.equals(job.owner()),
                review.settled(),
                doneAt == null
                        ? null
                        : instant(doneAt.plusHours(sources.settings().get().approveHours())),
                target == null ? null : target.y(),
                target == null ? null : target.surface(),
                stock,
                canManage(company, player),
                job.state() == JobState.OPEN
                        && !mine
                        && company.map(found -> found.member(job.owner()).isEmpty())
                                .orElse(false)
                        && company.map(jobs.service()::hasFreeSlot).orElse(false),
                extension.extendedHours(),
                extendable ? jobs.service().hoursLeft(job) : 0,
                extendable ? Math.max(0, settings.maxExtensionRequests() - extension.requests()) : 0,
                asked == null
                        ? null
                        : new ExtensionRequest(
                                asked,
                                Objects.requireNonNullElse(extension.reason(), ""),
                                instant(extension.askedAt())),
                job.kind() == JobKind.BUILD && !job.supplied()
                        ? jobs.costs()
                                .of(job.id())
                                .map(cost -> new MaterialCost(
                                        cost.total(), cost.unpriced().size()))
                                .orElse(null)
                        : null,
                job.state() == JobState.TAKEN
                        ? jobs.progress()
                                .last(job.id())
                                .map(work -> new Progress(work.percent(), work.cleared(), work.total()))
                                .orElse(null)
                        : null,
                job.state() == JobState.TAKEN && review.cancelAsked() != null,
                mine || ours(job, company)
                        ? jobs.data().reviews(job.id()).stream()
                                .map(entry -> new Review(
                                        entry.kind().name().toLowerCase(Locale.ROOT),
                                        entry.reason(),
                                        instant(entry.at())))
                                .toList()
                        : List.of(),
                job.supplied(),
                job.supplied() && mine && job.state() == JobState.DRAFT ? missing(jobs, job.id()) : null);
    }

    private static Map<String, Integer> missing(JobServices jobs, int id) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        jobs.supplies().missing(id).forEach((type, amount) -> missing.put(type.name(), amount));
        return missing;
    }

    private ActionResult supply(ActionRequest request, JobServices jobs, UUID player, JsonNode args) {
        var id = args.path("id").asInt(0);
        var draft = jobs.data().job(id).filter(job -> job.owner().equals(player) && job.state() == JobState.DRAFT);
        if (draft.isEmpty()) return answers.refused(request, OWNER + "error.noDraft");
        if (!draft.get().supplied()) {
            return answers.refused(request, CommandFailure.of(OWNER + "error.notSupplied", number(id)));
        }
        jobs.supplies().fromReturns(player, id, sources.orders());
        var company = sources.companyOf().apply(player).join();
        return answers.data(request, details(jobs, jobs.data().job(id).orElse(draft.get()), player, company));
    }

    private static boolean ours(Job job, Optional<CompanyProfile> company) {
        return company.map(found -> Integer.valueOf(found.id()).equals(job.company()))
                .orElse(false);
    }

    private @Nullable Instant instant(@Nullable LocalDateTime time) {
        return time == null ? null : sources.instant().apply(time);
    }

    private static boolean canManage(Optional<CompanyProfile> company, UUID player) {
        return company.flatMap(found -> found.member(player))
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private static TagResolver.Single number(int id) {
        return Values.number("id", id);
    }

    private static String camel(String name) {
        var parts = name.toLowerCase(Locale.ROOT).split("_");
        var key = new StringBuilder(parts[0]);
        for (var i = 1; i < parts.length; i++) {
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return key.toString();
    }

    /**
     * What the Jobs page is built with.
     *
     * @param jobs      the jobs of this server, null while they are not running
     * @param companyOf the company of a player
     * @param settings  the settings of jobs
     * @param instant   turns a stored time into an instant
     * @param now       the current time
     * @param render    renders a refusal as plain text
     * @param lastPlace where a player was last seen, as a block; empty when it is not known
     * @param returns   puts items into a player's item returns, as amounts by material name, and answers what could
     *                  not be stored
     * @param orders    the item returns materials are taken from
     */
    public record Sources(
            Supplier<@Nullable JobServices> jobs,
            Function<UUID, CompletableFuture<Optional<CompanyProfile>>> companyOf,
            Supplier<JobSettings> settings,
            Function<LocalDateTime, Instant> instant,
            Supplier<LocalDateTime> now,
            Function<CommandFailure, String> render,
            Function<UUID, Optional<JobArea>> lastPlace,
            BiFunction<UUID, Map<String, Integer>, Map<String, Integer>> returns,
            AOrderData orders) {}

    /**
     * A job as listed.
     *
     * @param id        id of the job
     * @param kind      its kind, lower case
     * @param state     its state, lower case
     * @param title     its title
     * @param price     its price, the budget of a harvest job
     * @param fee       the fee its owner paid
     * @param due       when the company's time runs out, null while nobody holds it
     * @param expires   when it expires while nobody takes it
     * @param blocks    the blocks of its area, 0 for a contract
     * @param world     the world of its area, null for a contract
     * @param center    the center of its area, null for a contract
     * @param unitPrice what a harvest job pays per item, null for other kinds
     * @param paid      what a harvest job paid so far, null for other kinds
     * @param mine      whether the player owns it
     * @param ours      whether the player's company holds it
     * @param node      the server whose world holds a draft's area, where it is posted; null for other states and
     *                  for drafts without an area
     * @param dueHours  how long a company has to finish it once taken
     */
    public record JobRow(
            int id,
            String kind,
            String state,
            String title,
            double price,
            double fee,
            @Nullable Instant due,
            @Nullable Instant expires,
            long blocks,
            @Nullable String world,
            @Nullable String center,
            @Nullable Double unitPrice,
            @Nullable Double paid,
            boolean mine,
            boolean ours,
            @Nullable UUID node,
            int dueHours) {}

    /**
     * The lists of the Jobs page.
     *
     * @param open      jobs waiting for a company, without those of the player's own company members
     * @param mine      the player's own jobs, the newest first
     * @param company   the jobs the player's company holds
     * @param inCompany whether the player belongs to a company
     * @param canManage whether the player may take and finish jobs for their company
     * @param canPost   whether the player may post jobs
     */
    public record JobList(
            List<JobRow> open,
            List<JobRow> mine,
            List<JobRow> company,
            boolean inCompany,
            boolean canManage,
            boolean canPost) {}

    /**
     * A job with what its page shows.
     *
     * @param job            the job
     * @param description    what the owner wrote about it
     * @param rejected       why the owner rejected the work last
     * @param dispute        why a dispute was opened
     * @param disputeByOwner whether the owner opened the dispute
     * @param settled        the percent an admin gave the company
     * @param approveAt      when a contract the company says is done is approved without an answer
     * @param targetY        the target height of a terraform job
     * @param surface        the surface block of a terraform job
     * @param stock          the stock of the player's harvest job, as amounts by material name
     * @param canManage      whether the player may take and finish jobs for their company
     * @param takeable       whether the player's company may take the job: it is open, owned by nobody of the company,
     *                       and the company has an order slot free
     * @param extendedHours  the hours the owner added for the company working on it
     * @param hoursLeft      the hours it may still get, 0 when it may get none
     * @param requestsLeft   how often the company working on it may still ask for more time
     * @param request        the company's open request for more time, null without one
     * @param materialCost   what the blocks of a build job's blueprint cost, null for other kinds
     * @param progress       how far the work was at the last check of its area, null when this server did not check it
     * @param cancelAsked    whether the owner asks the company working on it to let it be cancelled
     * @param reviews        the steps of the review of its work, oldest first; only for the owner and its company
     * @param supplied       whether the owner supplies the materials through the stock
     * @param missing        what the stock of a supplied draft still lacks, by material; only for its owner
     */
    public record JobDetails(
            JobRow job,
            @Nullable String description,
            @Nullable String rejected,
            @Nullable String dispute,
            boolean disputeByOwner,
            @Nullable Integer settled,
            @Nullable Instant approveAt,
            @Nullable Integer targetY,
            @Nullable String surface,
            Map<String, Integer> stock,
            boolean canManage,
            boolean takeable,
            int extendedHours,
            int hoursLeft,
            int requestsLeft,
            @Nullable ExtensionRequest request,
            @Nullable MaterialCost materialCost,
            @Nullable Progress progress,
            boolean cancelAsked,
            List<Review> reviews,
            boolean supplied,
            @Nullable Map<String, Integer> missing) {}

    /**
     * A step of the review of a job's work.
     *
     * @param kind   {@code done} when the company said the work is done, {@code rejected} when the owner sent it back
     * @param reason why it was sent back, null otherwise
     * @param at     when
     */
    public record Review(
            String kind, @Nullable String reason, @Nullable Instant at) {}

    /**
     * How far the work on a job was at the last check of its area.
     *
     * @param percent the share done
     * @param done    the blocks as the job asks
     * @param total   the blocks the job asks about
     */
    public record Progress(int percent, long done, long total) {}

    /**
     * What the blocks of a build job's blueprint cost.
     *
     * @param total    the cost of the blocks with a known price
     * @param unpriced how many block types have no known price and are left out
     */
    public record MaterialCost(double total, int unpriced) {}

    /**
     * The schematics a player uploaded for build jobs.
     *
     * @param open     whether the server takes uploads now
     * @param max      the most schematics a player may keep
     * @param maxBytes the largest file taken, in bytes
     * @param names    the names of the player's schematics, sorted
     */
    public record SchematicList(boolean open, int max, int maxBytes, List<String> names) {}

    /**
     * The areas a player stored for area jobs.
     *
     * @param max   the most areas a player may keep
     * @param areas the player's areas, sorted by name
     */
    public record AreaList(int max, List<AreaRow> areas) {}

    /**
     * The top layer of a stored area and the schematics a build job on it may use.
     *
     * @param name    the area's name
     * @param width   columns along x
     * @param length  columns along z
     * @param palette the colours as {@code #rrggbb}, an empty text for a column with nothing in the area
     * @param cells   the palette index of each column, row by row along z, each row along x
     * @param server  the server's schematics
     * @param own     the player's uploaded schematics
     */
    public record AreaPreview(
            String name,
            int width,
            int length,
            List<String> palette,
            int[] cells,
            List<String> server,
            List<String> own) {}

    /**
     * A schematic, turned, as a build job would ask for it.
     *
     * @param width    its size along x
     * @param height   its size along y
     * @param length   its size along z
     * @param blocks   how many blocks it asks for
     * @param cost     what its blocks cost at the server's prices
     * @param unpriced   how many block types have no known price and are left out
     * @param unmakeable the block types nobody can place, left out when the owner supplies the materials
     */
    public record SchematicFit(
            int width, int height, int length, int blocks, double cost, int unpriced, List<String> unmakeable) {}

    /**
     * A stored area.
     *
     * @param name   its name
     * @param node   the server whose world holds it, where jobs on it are posted
     * @param world  the world
     * @param center the block in the middle of its top layer
     * @param width  its size along x
     * @param height its size along y
     * @param length its size along z
     * @param blocks how many blocks it holds
     */
    public record AreaRow(
            String name, String node, String world, String center, int width, int height, int length, long blocks) {}

    /**
     * A schematic that was cleaned and kept.
     *
     * @param list     the player's schematics afterwards
     * @param name     its name
     * @param blocks   how many blocks its blueprint asks for
     * @param removed  how many blocks cleaning removed
     * @param data     how much block and entity data cleaning removed, such as the contents of a chest
     * @param entities how many entities cleaning removed
     */
    public record Uploaded(SchematicList list, String name, int blocks, int removed, int data, int entities) {}

    /**
     * The open request of the company working on a job for more time.
     *
     * @param hours  the hours it asks for
     * @param reason why
     * @param asked  when it asked
     */
    public record ExtensionRequest(
            int hours, String reason, @Nullable Instant asked) {}
}
