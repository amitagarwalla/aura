/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.impl.root.theme;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.auraframework.def.AttributeDef;
import org.auraframework.def.AttributeDefRef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.RegisterEventDef;
import org.auraframework.def.RootDefinition;
import org.auraframework.def.ThemeDef;
import org.auraframework.impl.root.RootDefinitionImpl;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.impl.util.AuraUtil;
import org.auraframework.throwable.quickfix.DefinitionNotFoundException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.json.Json;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Implementation for {@link ThemeDef}.
 * 
 * @author nmcwilliams
 */
public class ThemeDefImpl extends RootDefinitionImpl<ThemeDef> implements ThemeDef {
    private static final long serialVersionUID = -7900230831915100535L;

    private final int hashCode;
    private final DefDescriptor<ThemeDef> extendsDescriptor;
    private final Set<AttributeDefRef> overrides;

    public ThemeDefImpl(Builder builder) {
        super(builder);
        this.extendsDescriptor = builder.extendsDescriptor;
        this.overrides = AuraUtil.immutableSet(builder.overrides);
        this.hashCode = AuraUtil.hashCode(super.hashCode(), extendsDescriptor, overrides);
    }

    @Override
    public Optional<String> variable(String name) throws QuickFixException {
        AttributeDef attributeDef = getAttributeDefs().get(DefDescriptorImpl.getInstance(name, AttributeDef.class));

        if (attributeDef == null) {
            return Optional.absent();
        }

        AttributeDefRef defaultValue = attributeDef.getDefaultValue();
        assert defaultValue != null : "default values should be set";

        return Optional.of(defaultValue.getValue().toString());
    }

    @Override
    public DefDescriptor<ThemeDef> getExtendsDescriptor() {
        return extendsDescriptor;
    }

    @Override
    public Set<AttributeDefRef> getOverrides() {
        return overrides;
    }

    @Override
    public void validateDefinition() throws QuickFixException {
        super.validateDefinition();

        // attributes
        for (AttributeDef attribute : attributeDefs.values()) {
            attribute.validateDefinition();

            // check that each attribute has default specified
            if (attribute.getDefaultValue() == null) {
                String msg = "Attribute '%s' must specify a default value. An empty string is acceptable.";
                throw new InvalidDefinitionException(String.format(msg, attribute.getName()), getLocation());
            }
        }

        // overrides
        for (AttributeDefRef override : overrides) {
            override.validateDefinition();
        }
    }

    @Override
    public void validateReferences() throws QuickFixException {
        super.validateReferences();

        // extends
        if (extendsDescriptor != null) {
            if (extendsDescriptor.equals(descriptor)) {
                String msg = "%s cannot extend itself";
                throw new InvalidDefinitionException(String.format(msg, getDescriptor()), getLocation());
            }
            if (extendsDescriptor.getDef() == null) {
                throw new DefinitionNotFoundException(extendsDescriptor, getLocation());
            }
        }

        // attributes
        for (AttributeDef att : this.attributeDefs.values()) {
            att.validateReferences();
        }

        // overrides
        for (AttributeDefRef override : overrides) {
            override.validateReferences();

            String msg = "Attribute '%s' is not inherited.";
            DefDescriptor<AttributeDef> desc = override.getDescriptor();

            // ensure it is actually overriding something
            if (extendsDescriptor == null) {
                throw new InvalidDefinitionException(String.format(msg, desc.getName()), getLocation());
            } else {
                ThemeDef parent = extendsDescriptor.getDef();
                AttributeDef overridden = parent.getAttributeDefs().get(desc);
                if (overridden == null) {
                    throw new InvalidDefinitionException(String.format(msg, desc.getName()), getLocation());
                }
            }
        }
    }

    @Override
    public void appendDependencies(Set<DefDescriptor<?>> dependencies) throws QuickFixException {
        super.appendDependencies(dependencies);
        if (extendsDescriptor != null) {
            dependencies.add(extendsDescriptor);
        }
    }

    @Override
    public Map<String, RegisterEventDef> getRegisterEventDefs() {
        return null; // events not supported here
    }

    @Override
    public boolean isInstanceOf(DefDescriptor<? extends RootDefinition> other) {
        switch (other.getDefType()) {
        case THEME:
            return descriptor.equals(other);
        default:
            return false;
        }
    }

    @Override
    public List<DefDescriptor<?>> getBundle() {
        return Lists.newArrayList();
    }

    @Override
    public void serialize(Json json) throws IOException {
        json.writeMapBegin();
        json.writeMapEntry("attributes", attributeDefs);
        json.writeMapEnd();
    }

    @Override
    public Map<DefDescriptor<AttributeDef>, AttributeDef> getAttributeDefs() throws QuickFixException {
        // self and parent attributes
        Map<DefDescriptor<AttributeDef>, AttributeDef> ret = new LinkedHashMap<DefDescriptor<AttributeDef>, AttributeDef>();
        if (extendsDescriptor != null) {
            ret.putAll(extendsDescriptor.getDef().getAttributeDefs());
            ret.putAll(attributeDefs);
            return Collections.unmodifiableMap(ret);
        }

        return attributeDefs;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ThemeDefImpl) {
            ThemeDefImpl other = (ThemeDefImpl) obj;
            return Objects
                    .equal(descriptor, other.descriptor)
                    && Objects.equal(location, other.location)
                    && Objects.equal(attributeDefs, other.attributeDefs)
                    && Objects.equal(extendsDescriptor, other.extendsDescriptor)
                    && Objects.equal(overrides, other.overrides);
        }

        return false;
    }

    /**
     * Utility to get {@link DefDescriptor}s for themes.
     * 
     * @param name Descriptor of the theme to get, e.g., "namespace:themeName".
     */
    public static DefDescriptor<ThemeDef> descriptor(String name) {
        return DefDescriptorImpl.getInstance(name, ThemeDef.class);
    }

    /**
     * Used to build instances of {@link ThemeDef}s.
     */
    public static class Builder extends RootDefinitionImpl.Builder<ThemeDef> {
        public DefDescriptor<ThemeDef> extendsDescriptor;
        public Set<AttributeDefRef> overrides = Sets.newHashSet();

        public Builder() {
            super(ThemeDef.class);
        }

        @Override
        public ThemeDefImpl build() {
            return new ThemeDefImpl(this);
        }

        public void addOverride(AttributeDefRef ref) {
            overrides.add(ref);
        }
    }
}
