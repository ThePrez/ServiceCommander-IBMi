package jesseg.ibmi.opensource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import com.github.theprez.jcmdutils.AppLogger;

import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAlive;
import jesseg.ibmi.opensource.utils.ListUtils;

public class ServiceDefinitionCollection {
    private final TreeMap<String, ServiceDefinition> m_data = new TreeMap<String, ServiceDefinition>();

    public ServiceDefinitionCollection() {
    }

    public void checkForCheckaliveConflicts(final AppLogger _logger) {
        final LinkedList<ServiceDefinition> unprocessed = new LinkedList<ServiceDefinition>();
        unprocessed.addAll(m_data.values());
        final HashMap<CheckAlive, List<ServiceDefinition>> conflicts = new HashMap<CheckAlive, List<ServiceDefinition>>();
        while (!unprocessed.isEmpty()) {
            final ServiceDefinition current = unprocessed.removeFirst();
            final List<CheckAlive> currentCheckalives = current.getCheckAlives();
            for (final ServiceDefinition comp : unprocessed) {
                final List<CheckAlive> compCheckAlives = comp.getCheckAlives();
                final List<CheckAlive> intersection = ListUtils.intersection(currentCheckalives, compCheckAlives);
                for (final CheckAlive conflict : intersection) {
                    List<ServiceDefinition> conflictList = conflicts.get(conflict);
                    if (null == conflictList) {
                        conflictList = new LinkedList<ServiceDefinition>();
                    }
                    conflictList.add(comp);
                    conflictList.add(current);
                    conflicts.put(conflict, conflictList);
                }
            }
        }

        for (final Entry<CheckAlive, List<ServiceDefinition>> entry : conflicts.entrySet()) {
            final List<ServiceDefinition> defs = ListUtils.deduplicate(entry.getValue());
            if (1 > defs.size()) {
                continue;
            }
            String warningStr = "WARNING: the following services all have conflicting definitions for liveliness check '" + entry.getKey() + "':";
            for (final ServiceDefinition def : defs) {
                warningStr += "\n    " + def.getName() + " (" + def.getFriendlyName() + ")";
            }
            _logger.println_warn(warningStr);
        }
    }

    public ServiceDefinition get(final String _name) {
        return getService(_name);
    }

    public ServiceDefinition getService(final String _name) {
        return m_data.get(_name);
    }

    public String getServiceFriendlyName(final String _name) {
        final ServiceDefinition svc = getService(_name);
        if (null == svc) {
            return "";
        }
        return svc.getFriendlyName();
    }

    public List<ServiceDefinition> getServices() {
        return new ArrayList<ServiceDefinition>(m_data.values());
    }

    public Set<String> getServicesInGroup(final String _group, final AppLogger _logger) {
        _logger.printfln_verbose("Looking for services in group '%s'", _group);
        final LinkedHashSet<String> ret = new LinkedHashSet<String>();
        for (final ServiceDefinition svcDef : m_data.values()) {
            if ("all".equalsIgnoreCase(_group)) {
                ret.add(svcDef.getName());
                continue;
            }
            if (svcDef.isInGroup(_group)) {
                ret.add(svcDef.getName());
            }
        }
        if (ret.isEmpty()) {
            _logger.printfln_warn("WARNING: No services are found in group '%s'", _group);
        } else {
            _logger.printfln_verbose("Services in group '%s' are: %s", _group, ret);
        }
        return ret;
    }

    public Set<String> getServicesNotInGroup(final String _group, final AppLogger _logger) {
        _logger.printfln_verbose("Looking for services not in group '%s'", _group);
        final LinkedHashSet<String> ret = new LinkedHashSet<String>();
        for (final ServiceDefinition svcDef : m_data.values()) {
            if ("all".equalsIgnoreCase(_group)) {
                continue;
            }
            if (!svcDef.isInGroup(_group)) {
                ret.add(svcDef.getName());
            }
        }
        if (ret.isEmpty()) {
            _logger.printfln_warn("WARNING: No services are found not in group '%s'", _group);
        } else {
            _logger.printfln_verbose("Services not in group '%s' are: %s", _group, ret);
        }
        return ret;
    }

    public void put(final ServiceDefinition _serviceDef) {
        m_data.put(_serviceDef.getName(), _serviceDef);
    }

    public void putAll(final ServiceDefinitionCollection _c) {
        m_data.putAll(_c.m_data);
    }

    public void removeServicesInGroup(final String _retain, final String... _groups) {
        final List<String> toRemove = new LinkedList<String>();
        for (final Entry<String, ServiceDefinition> entry : m_data.entrySet()) {
            if (entry.getValue().getName().equalsIgnoreCase(_retain)) {
                continue;
            }
            for (final String group : _groups) {
                if (entry.getValue().isInGroup(group)) {
                    toRemove.add(entry.getKey());
                }
            }
        }
        for (final String removal : toRemove) {
            m_data.remove(removal);
        }
    }

    public void validateNoCircularDependencies(final AppLogger _logger) throws SCException {
        for (final ServiceDefinition def : getServices()) {
            validateNoCircularDependencies(_logger, def, new Stack<String>());
        }
    }

    private void validateNoCircularDependencies(final AppLogger _logger, final ServiceDefinition _def, final Stack<String> _dependencyStack) throws SCException {
        if (null == _def) {
            return;
        }
        final int idx = _dependencyStack.indexOf(_def.getName());
        if (-1 != idx) {
            _dependencyStack.push(_def.getName());
            String errorStr = "Circular dependency detected in service definitions!!";
            for (int i = idx; i < -1 + _dependencyStack.size(); ++i) {
                final String svc1 = _dependencyStack.get(i);
                final String svc2 = _dependencyStack.get(1 + i);
                errorStr += String.format("\n    %s (%s) depends on ----> %s (%s)", svc1, getServiceFriendlyName(svc1), svc2, getServiceFriendlyName(svc2));
            }
            throw new SCException(_logger, FailureType.INVALID_SERVICE_CONFIG, errorStr);
        }
        _dependencyStack.push(_def.getName());
        for (final String dependency : _def.getDependencies()) {
            validateNoCircularDependencies(_logger, getService(dependency), _dependencyStack);
        }
    }
}
