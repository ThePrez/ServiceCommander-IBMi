package jesseg.ibmi.opensource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import com.github.theprez.jcmdutils.AppLogger;

import jesseg.ibmi.opensource.SCException.FailureType;

public class ServiceDefinitionCollection {
    private final Hashtable<String, ServiceDefinition> m_data = new Hashtable<String, ServiceDefinition>();

    public ServiceDefinitionCollection() {
    }

    public void checkForCheckaliveConflicts(final AppLogger _logger) {
        final HashMap<String, List<ServiceDefinition>> defsByCheckalive = new HashMap<String, List<ServiceDefinition>>();
        for (final ServiceDefinition def : getServices()) {
            final String checkAliveKey = "" + def.getCheckAliveType().name() + ":" + def.getCheckAliveCriteria();
            List<ServiceDefinition> currentList = defsByCheckalive.get(checkAliveKey);
            if (null == currentList) {
                defsByCheckalive.put(checkAliveKey, currentList = new LinkedList<ServiceDefinition>());
            }
            currentList.add(def);
        }
        for (final Entry<String, List<ServiceDefinition>> entry : defsByCheckalive.entrySet()) {
            final List<ServiceDefinition> defs = entry.getValue();
            if (1 >= defs.size()) {
                continue;
            }
            String warningStr = "WARNING: the following services all have conflicting definitions for liveliness check " + entry.getKey() + ":";
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
            for (final String svcGroup : svcDef.getGroups()) {
                if (svcGroup.trim().equalsIgnoreCase(_group)) {
                    ret.add(svcDef.getName());
                }
            }
        }
        if (ret.isEmpty()) {
            _logger.printfln_warn("WARNING: No services are found in group '%s'", _group);
        } else {
            _logger.printfln_verbose("Services in group '%s' are: %s", _group, ret);
        }
        return ret;
    }

    public void put(final ServiceDefinition _serviceDef) {
        m_data.put(_serviceDef.getName(), _serviceDef);
    }

    public void putAll(final ServiceDefinitionCollection _c) {
        m_data.putAll(_c.m_data);
    }

    public void validateNoCircularDependencies(final AppLogger _logger) throws SCException {
        for (final ServiceDefinition def : getServices()) {
            validateNoCircularDependencies(_logger, def, new Stack<String>());
        }
    }

    private void validateNoCircularDependencies(final AppLogger _logger, final ServiceDefinition _def, final Stack<String> _dependencyStack) throws SCException {
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
