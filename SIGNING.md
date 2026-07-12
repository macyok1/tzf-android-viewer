# TZF Viewer signing

Nightly APKs are signed by the dedicated TZF Viewer release key.

Certificate SHA-256:

```text
8a9f0eddc84bafa7ea28d78151bfd18b3c9de8cf06df754b6ffc33d68de092b8
```

The keystore and recovery credentials are not stored in Git. GitHub Actions uses repository secrets:

- `TZF_KEYSTORE_BASE64`
- `TZF_STORE_PASSWORD`
- `TZF_KEY_ALIAS`
- `TZF_KEY_PASSWORD`

The local recovery copy is stored outside the repository in `C:\Users\PC\.tzf-signing`. Back up that directory securely. Losing both this directory and the GitHub secrets makes it impossible to publish an APK that updates existing release installations.
