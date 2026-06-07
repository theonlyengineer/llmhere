# Vendored submodule patches

Patches applied on top of the pinned `third_party/llama.cpp` submodule. They live
here because git records a submodule by commit SHA, not by working-tree content, so
local edits inside the submodule are not captured by parent-repo commits.

Apply after `git submodule update --init --recursive`:

```bash
git -C third_party/llama.cpp apply ../../patches/llama.cpp-falcon_e-pretokenizer.patch
```

## `llama.cpp-falcon_e-pretokenizer.patch`

Adds the `falcon_e` BPE pre-tokenizer to mainline llama.cpp. Falcon-E GGUFs declare
`tokenizer.ggml.pre = falcon_e`, which upstream b9501 does not recognize and rejects
with `unknown pre-tokenizer type: 'falcon_e'`. The regex matches the `falcon` type
except digits are split individually (`[0-9]`) rather than in groups of three.

TODO: upstream this, or move the submodule to a fork that carries it, so a fresh
clone builds without the manual apply step.
