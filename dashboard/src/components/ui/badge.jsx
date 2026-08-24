import * as React from "react";
import { cva } from "class-variance-authority";
import { cn } from "../../lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wider transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-indigo-600 text-white shadow",
        secondary:
          "border-transparent bg-gray-800 text-gray-200",
        destructive:
          "border-red-500/30 bg-red-500/15 text-red-400",
        outline: "text-gray-300 border-gray-700",
        critical: "border-red-500/30 bg-red-500/15 text-red-400",
        high: "border-orange-500/30 bg-orange-500/15 text-orange-400",
        medium: "border-amber-500/30 bg-amber-500/15 text-amber-400",
        low: "border-blue-500/30 bg-blue-500/15 text-blue-400",
        open: "border-rose-500/30 bg-rose-500/15 text-rose-400",
        investigating: "border-sky-500/30 bg-sky-500/15 text-sky-400",
        resolved: "border-emerald-500/30 bg-emerald-500/15 text-emerald-400",
        closed: "border-gray-600/30 bg-gray-600/15 text-gray-400",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

function Badge({ className, variant, ...props }) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  );
}

export { Badge, badgeVariants };
