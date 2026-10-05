import React from 'react';

export interface BreadcrumbItem {
  label: string;
  href?: string;
}

export interface BreadcrumbProps {
  id?: string;
  items?: BreadcrumbItem[];
  className?: string;
}

export default function Breadcrumb({ element, props: directProps, children }: any) {
  const props: BreadcrumbProps = element?.props ?? directProps ?? {};
  const items: BreadcrumbItem[] = props.items ?? [];

  return (
    <nav aria-label="breadcrumb" className={props.className}>
      <ol className="flex items-center space-x-2 text-sm text-muted-foreground">
        {items.map((item, idx) => (
          <li key={idx} className="flex items-center space-x-2">
            {idx > 0 && <span className="select-none text-muted-foreground/60">/</span>}
            {item.href ? (
              <a href={item.href} className="hover:text-foreground transition-colors">
                {item.label}
              </a>
            ) : (
              <span className={idx === items.length - 1 ? 'font-medium text-foreground' : ''}>
                {item.label}
              </span>
            )}
          </li>
        ))}
        {children}
      </ol>
    </nav>
  );
}
