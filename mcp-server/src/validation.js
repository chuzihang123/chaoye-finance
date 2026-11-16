import Ajv from 'ajv';
import addFormats from 'ajv-formats';

export function resolveRef(value, spec) {
  const seen = new Set();
  while (value?.$ref) {
    const ref = value.$ref;
    if (!ref.startsWith('#/') || seen.has(ref)) throw new Error('不支持的或循环的 OpenAPI 引用');
    seen.add(ref);
    value = ref.slice(2).split('/').reduce((node, key) => node?.[key.replace(/~1/g, '/').replace(/~0/g, '~')], spec);
    if (!value) throw new Error('OpenAPI 引用不存在');
  }
  return value;
}

// OpenAPI 3.0 uses boolean exclusive bounds, unlike JSON Schema draft 7.
function normalize(value) {
  if (Array.isArray(value)) return value.map(normalize);
  if (!value || typeof value !== 'object') return value;
  const result = Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, normalize(entry)]));
  for (const [exclusive, inclusive] of [['exclusiveMinimum', 'minimum'], ['exclusiveMaximum', 'maximum']]) {
    if (typeof result[exclusive] === 'boolean') {
      if (result[exclusive] && typeof result[inclusive] === 'number') result[exclusive] = result[inclusive];
      else delete result[exclusive];
    }
  }
  return result;
}

export function validateSchema(schema, value, spec, label) {
  if (!schema) return;
  const ajv = new Ajv({ strict: false, allErrors: true, coerceTypes: false, validateFormats: true });
  addFormats(ajv);
  // Register the document so nested and recursive local schema references resolve.
  const rootId = 'urn:jinbooks:openapi';
  ajv.addSchema(normalize({ ...spec, $id: rootId }), rootId);
  const rewrite = node => {
    if (Array.isArray(node)) return node.map(rewrite);
    if (!node || typeof node !== 'object') return node;
    return Object.fromEntries(Object.entries(node).map(([key, entry]) => {
      if (key === '$ref') {
        if (!entry.startsWith('#/')) throw new Error('不支持外部 OpenAPI 引用');
        return [key, rootId + entry];
      }
      return [key, rewrite(entry)];
    }));
  };
  let validate;
  try {
    validate = ajv.compile(rewrite(normalize(schema)));
  } catch {
    throw new Error(`${label} 的 OpenAPI Schema 无法解析`);
  }
  if (!validate(value)) {
    // Report schema locations and rules without echoing sensitive input values.
    throw new Error(`${label} 校验失败 ${validate.errors.map(error => `${error.instancePath || '/'} ${error.keyword} ${error.message}`).join('，')}`);
  }
}
