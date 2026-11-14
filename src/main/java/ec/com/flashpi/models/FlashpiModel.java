package ec.com.flashpi.models;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base of every model: the API's objects, with typed getters and a builder.
 *
 * <p>A model keeps which members were set, so a member set to null is sent as null while a member
 * left unset is not sent at all (what a PATCH needs), and a model read from the API is written back
 * the same way. Members this version of the SDK does not know are kept in {@link
 * #getAdditionalProperties()}. Models are immutable, compare by value and never show a secret in
 * {@link #toString()}.
 */
public abstract class FlashpiModel implements Serializable {
  private static final long serialVersionUID = 1L;

  // JSON values: maps, lists, text, numbers, dates and models, all serializable.
  @SuppressWarnings("serial")
  private final Map<String, Object> members;

  @SuppressWarnings("serial")
  private final Map<String, Object> additionalProperties;

  FlashpiModel(Map<String, Object> members, Map<String, Object> additionalProperties) {
    this.members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    this.additionalProperties =
        Collections.unmodifiableMap(new LinkedHashMap<>(additionalProperties));
  }

  /** The members of this class: their names and types. */
  abstract ModelType modelType();

  /** The value of a member, or null when it is null or not set. */
  final Object get(String member) {
    return members.get(member);
  }

  /** The members that are set, in order. */
  final Map<String, Object> members() {
    return members;
  }

  /**
   * Whether a member is set: present in the JSON the model was read from (even as null), or set on
   * its builder.
   *
   * @param member the member's name, as in the API ({@code "tradeName"})
   * @return true when it is set
   */
  public final boolean has(String member) {
    return members.containsKey(member);
  }

  /**
   * Members this version of the SDK does not know, as read from the API (objects as maps, arrays as
   * lists), or set with {@link Builder#additionalProperty(String, Object)}.
   *
   * @return the members by name; unmodifiable
   */
  public final Map<String, Object> getAdditionalProperties() {
    return additionalProperties;
  }

  // Every model class is final, and two classes with the same members are still different models.
  @SuppressWarnings("EqualsGetClass")
  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    FlashpiModel model = (FlashpiModel) other;
    return members.equals(model.members) && additionalProperties.equals(model.additionalProperties);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getClass(), members, additionalProperties);
  }

  /** The class and its members; a secret member shows as {@code ***}. */
  @Override
  public final String toString() {
    StringBuilder out = new StringBuilder(getClass().getSimpleName()).append('{');
    String separator = "";
    ModelType type = modelType();
    for (Map.Entry<String, Object> member : members.entrySet()) {
      out.append(separator).append(member.getKey()).append('=');
      out.append(
          type.isSecret(member.getKey()) && member.getValue() != null ? "***" : member.getValue());
      separator = ", ";
    }
    for (Map.Entry<String, Object> member : additionalProperties.entrySet()) {
      out.append(separator).append(member.getKey()).append('=').append(member.getValue());
      separator = ", ";
    }
    return out.append('}').toString();
  }

  /**
   * Base of the builders. A member is sent once it is set, null included; a member left unset is
   * not sent.
   *
   * @param <B> the builder
   * @param <M> the model it builds
   */
  public abstract static class Builder<B extends Builder<B, M>, M extends FlashpiModel> {
    private final ModelType type;
    private final Map<String, Object> members = new LinkedHashMap<>();
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    Builder(ModelType type) {
      this.type = type;
    }

    @SuppressWarnings("unchecked")
    final B set(String member, Object value) {
      members.put(member, value);
      return (B) this;
    }

    final void copy(FlashpiModel model) {
      members.putAll(model.members);
      additionalProperties.putAll(model.additionalProperties);
    }

    /**
     * Sets a member this version of the SDK does not know yet, sent as it is (any value Jackson can
     * write).
     *
     * @param name the member's name, as in the API
     * @param value its value; null sends null
     * @return this builder
     * @throws IllegalArgumentException when the SDK knows the member: use its own method
     */
    @SuppressWarnings("unchecked")
    public final B additionalProperty(String name, Object value) {
      Objects.requireNonNull(name, "name");
      if (type.has(name)) {
        throw new IllegalArgumentException(
            name + " is a member of " + type.name() + ": set it with " + name + "(...)");
      }
      additionalProperties.put(name, value);
      return (B) this;
    }

    /**
     * Sets several members this version of the SDK does not know yet; see {@link
     * #additionalProperty(String, Object)}.
     *
     * @param properties the members by name
     * @return this builder
     */
    @SuppressWarnings("unchecked")
    public final B additionalProperties(Map<String, ?> properties) {
      for (Map.Entry<String, ?> property : properties.entrySet()) {
        additionalProperty(property.getKey(), property.getValue());
      }
      return (B) this;
    }

    /**
     * Builds the model. The API checks the members: a missing required one is answered with a
     * {@code validation_failed} error.
     *
     * @return the model
     */
    @SuppressWarnings("unchecked")
    public final M build() {
      return (M) type.create(members, additionalProperties);
    }
  }
}
