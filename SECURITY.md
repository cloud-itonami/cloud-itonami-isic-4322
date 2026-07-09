# Security Policy

This project handles permit, inspection and commissioning-record
workflows. Treat vulnerabilities as potentially high impact even when
the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- license/permit exposure
- real customer or site data exposure
- authorization bypass
- Plumbing Trade Governor bypass
- audit-ledger tampering
- over-disclosure in inspection/commissioning records or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on permit/site data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real customer and site data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
