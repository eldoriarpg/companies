/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.dashboard.actions;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.services.ActionResult.Done;
import de.eldoria.companies.services.ActionResult.Refused;
import de.eldoria.companies.web.api.Protocol;
import de.eldoria.companies.web.api.action.ActionRequest;
import de.eldoria.companies.web.api.action.ActionResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the answers of the dashboard's actions: data for a done action, and for a refused one the locale key as
 * reason with the rendered text as message. Also reads the arguments every action shares.
 */
final class Answers {
    private final JsonMapper mapper = Protocol.mapper();
    private final Function<CommandFailure, String> render;

    /**
     * @param render renders a refusal as plain text, with its values
     */
    Answers(Function<CommandFailure, String> render) {
        this.render = render;
    }

    /**
     * @param request the action
     * @param value   what the action answers, written as JSON
     * @return a done answer
     */
    ActionResult data(ActionRequest request, Object value) {
        return new ActionResult(request.id(), Protocol.VERSION, true, null, null, mapper.valueToTree(value));
    }

    /**
     * @param request the action
     * @param failure why it was refused
     * @return a refused answer
     */
    ActionResult refused(ActionRequest request, CommandFailure failure) {
        return refused(request, failure.localeKey(), render.apply(failure));
    }

    /**
     * @param request the action
     * @param reason  the locale key of the refusal
     * @return a refused answer
     */
    ActionResult refused(ActionRequest request, String reason) {
        return refused(request, CommandFailure.of(reason));
    }

    /**
     * @param request the action
     * @param reason  the locale key of the refusal
     * @param message the refusal as the player reads it
     * @return a refused answer
     */
    ActionResult refused(ActionRequest request, String reason, String message) {
        return new ActionResult(request.id(), Protocol.VERSION, false, reason, message, null);
    }

    /**
     * Answers a service's result.
     *
     * @param request the action
     * @param result  the service's result
     * @param value   what a done action answers
     * @param <T>     the type of the done value
     * @return the answer
     */
    <T> ActionResult answer(
            ActionRequest request, de.eldoria.companies.services.ActionResult<T> result, Function<T, Object> value) {
        return switch (result) {
            case Done<T>(var done) -> data(request, value.apply(done));
            case Refused<T>(var failure) -> refused(request, failure);
        };
    }

    /**
     * @return an empty object for the arguments of an action without any
     */
    JsonNode empty() {
        return mapper.createObjectNode();
    }

    /**
     * Reads a player named by id.
     *
     * @param args  the arguments
     * @param field the field holding the id
     * @return the id, empty when the field holds none
     */
    static Optional<UUID> player(JsonNode args, String field) {
        try {
            return Optional.of(UUID.fromString(args.path(field).asString("")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * @param args the arguments
     * @return whether the action asks for a quote instead of being performed
     */
    static boolean quote(JsonNode args) {
        return args.path("quote").asBoolean(false);
    }
}
